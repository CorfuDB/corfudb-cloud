#!/usr/bin/env python
import re
import sys
import traceback

from correctness.client_report import ClientReport
from correctness.correctness_report import CorrectnessReport
from correctness.operation_factory import OperationFactory
from correctness.state import State
from utils.utils import count_lines

# Exception pattern in output file
exception_pattern = re.compile(".*\.(.[^ ]*?Exception): ")


class CorrectnessVerificatorAdapter:
    """The adapter should always be prefered against the Normal verificator.

       The adapter lets you choose the underlying implementation of
       verification you want to chose. If you set the demultiplex flag
       to true, you will use the demultiplexer implementation that is
       suited for very long runs.

       On long runs, the amount of history created is very large. On big
       history, the normal verification process is not suitable. It will
       starve the memory. The demultiplexer allows to create a history per
       map and then having each map correctness verified in parallel.
    """

    def __init__(self, file_paths, verbose=False, console=False, demultiplex=False, exception_paths=None,
                 print_report=False):
        """Adapter to choose if we want to use demultiplexer

        Args:
           file_paths (list): list of file_paths to analyze
           verbose (default False): for verbose output
           console (default False): if invoked from console
           demultiplex (default False): whether we use the demultiplexer

        """
        self.paths = file_paths
        self.verbose = verbose
        self.console = console
        self.demultiplex = demultiplex
        self.exception_paths = exception_paths
        self.print_report = print_report

    def increment_counter_dict(self, key, counter) -> None:
        """Increment the number of occurence of a key

        Args:
           key (obj): key of the dicitionary
           counter (obj): dictrionary

        Returns:
           None

        """
        counter[key] = 1 if key not in counter else counter[key] + 1

    def compile_exceptions_report(self):
        """Gather exception report"""

        exception_counts = {}
        if self.exception_paths is None:
            return exception_counts

        for exception_path in self.exception_paths:
            with open(exception_path) as f:
                for line in f:
                    match = exception_pattern.match(line)
                    if match is not None:
                        self.increment_counter_dict(match.group(1), exception_counts)
        return exception_counts

    def verify_base(self) -> CorrectnessReport:
        correctness_verificator = CorrectnessVerificator(
            self.paths,
            console=self.console,
            verbose=self.verbose,
            print_report=False)

        return correctness_verificator.verify()

    def verify(self) -> CorrectnessReport:
        """Pick the right mode (demlutiplexer or not) and verify

        Returns:
           aggregatable report of correctness

        """
        exceptions = self.compile_exceptions_report()

        report = self.verify_base()

        report.add_exceptions_count(exceptions)
        if self.print_report:
            report.print_report()
        return report


class CorrectnessVerificator:
    def __init__(self, file_paths, print_report=True, verbose=False, console=False):
        """
        Args:
           file_paths: list of path to "correctness.log" files
        """
        self.paths = file_paths
        self.operation_count = 0
        self.state = State()
        self.print_report = print_report
        self.verbose = verbose
        self.console = console

        self.client_count = 0
        self.client_reports = {}

    def add_file_history(self, path) -> None:
        number_of_operations = count_lines(path)
        if self.console:
            print("File Loaded in memory (%d operations)\n" % number_of_operations)
        file_operation_counter = 0

        # Initialize report for this client/file
        client_id = self.client_count
        self.client_reports[client_id] = ClientReport(client_id)

        with open(path) as f:
            for raw_line in f:
                line = raw_line.strip()
                try:
                    op = OperationFactory.create_operation(line, self.client_count)
                    op.add_to_history(self.state)
                    self.client_reports[client_id].report_operation(op, line)
                except Exception as e:
                    print(e)
                    traceback.print_exc()
                    print("Line number: " + str(file_operation_counter))
                    print("Invalid line: " + raw_line)

                if self.console and (self.operation_count % 1000 == 0):
                    percentage = (file_operation_counter / number_of_operations * 100)
                    print("processed operations: %d%%" % percentage, end='\r')
                    sys.stdout.flush()

                file_operation_counter += 1
                self.operation_count += 1

        self.client_count += 1

    def generate_report(self) -> CorrectnessReport:
        """Generate the correctness report of this run

        Args:
           self

        Returns:
           Report for correctness run

        """
        success = self.state.incorrect_read == 0
        num_maps = len(self.state.maps)
        num_keys = len(self.state.keys)
        num_inconsistencies = self.state.incorrect_read
        # For now not recorded
        num_cp = 0

        correctness_report = CorrectnessReport(
            success,
            num_maps,
            num_keys,
            num_inconsistencies,
            num_cp,
            self.client_reports)

        if self.print_report:
            correctness_report.print_report()
        return correctness_report

    def verify(self) -> CorrectnessReport:
        """Build the history and assess consistency of reads

        Args:
           self

        Returns:
           Report for correctness run

        """
        print("Add files to history")
        for file_path in self.paths:
            print("Add file: ", file_path)
            self.add_file_history(file_path)

        print("Verify the state")
        self.state.verify()
        print("Generate report")
        return self.generate_report()
