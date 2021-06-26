from __future__ import annotations

from correctness.liveness_operation import LivenessOperation
from correctness.read_operation import ReadOperation
from correctness.rm_operation import RemoveOperation
from correctness.tx_operation import TxOperation
from correctness.tx_read_operation import TxReadOperation
from correctness.tx_rm_operation import TxRemoveOperation
from correctness.tx_write_operation import TxWriteOperation
from correctness.write_operation import WriteOperation
from utils.utils import print_color_header


class ClientReport:
    """Contains information about client operations """

    def __init__(self, client_id):
        self.client_id = client_id
        '''
        FIXME: For now, this is true by default
        When we will have this in the loadgenerator
        we will set this flag accordingly

        Liveness is successful if the client was not stuck
        during the run. If the client got stuck but was
        able to self-heal, this still counts as success

        '''
        self.liveness_success = True
        self.checkpoint_count = 0

        self.snap_tx_counters = {"start": 0,
                                 "aborted": 0,
                                 "end": 0}

        self.opt_tx_counters = {"start": 0,
                                "aborted": 0,
                                "end": 0}

        self.operation_counters = {ReadOperation: 0,
                                   WriteOperation: 0,
                                   RemoveOperation: 0,
                                   TxReadOperation: 0,
                                   TxWriteOperation: 0,
                                   TxRemoveOperation: 0}

    @property
    def operation_count(self) -> int:
        global_count = 0
        for operation, count in self.operation_counters.items():
            global_count += count
        return global_count

    @property
    def tx_read_count(self) -> int:
        return self.operation_counters[TxReadOperation]

    @property
    def tx_write_count(self) -> int:
        return self.operation_counters[TxWriteOperation]

    @property
    def tx_rm_count(self) -> int:
        return self.operation_counters[TxRemoveOperation]

    @property
    def read_count(self) -> int:
        return self.operation_counters[ReadOperation]

    @property
    def write_count(self) -> int:
        return self.operation_counters[WriteOperation]

    @property
    def rm_count(self) -> int:
        return self.operation_counters[RemoveOperation]

    @property
    def snap_tx_count(self) -> int:
        return self.snap_tx_counters["start"]

    @property
    def snap_tx_aborted(self) -> int:
        return self.snap_tx_counters["aborted"]

    @property
    def snap_tx_committed(self):
        return self.snap_tx_counters["end"]

    @property
    def opt_tx_count(self) -> int:
        return self.opt_tx_counters["start"]

    @property
    def opt_tx_aborted(self) -> int:
        return self.opt_tx_counters["aborted"]

    @property
    def opt_tx_committed(self) -> int:
        return self.opt_tx_counters["end"]

    def report_liveness(self, liveness_operation: LivenessOperation):
        self.liveness_success = liveness_operation.success

    def report_transaction_operation(self, operation: TxOperation, state) -> None:
        if operation.tx_type == "TxSnap":
            self.snap_tx_counters[state] += 1
        elif operation.tx_type == "TxOpt":
            self.opt_tx_counters[state] += 1

    def report_operation(self, operation, line) -> None:
        """ Some operations (e.g. Transaction start/stop/abort)
            are copied in all the file concerned by it. If we just
            add all of them, we will count same operation many times.

            In our Transaction example, each map that has been touched
            by the transaction will have a TxStart in its file.

            To avoid counting them multiple time, the demultiplexer added
            a marker to the duplicates. All line that start with a "*" should
            not be counted.
        """
        if line.startswith("*"):
            return

        if isinstance(operation, TxOperation):
            self.report_transaction_operation(operation, operation.tx_state)
        elif isinstance(operation, LivenessOperation):
            self.report_liveness(operation)
        else:
            operation_type = type(operation)
            if operation_type in self.operation_counters:
                self.operation_counters[operation_type] += 1

    def print_report(self) -> None:
        print_color_header("Report for client {}".format(self.client_id))
        print("Liveness success: {}".format(self.liveness_success))
        print("Total number of operations: {}".format(self.operation_count))
        print("======================")
        print("Number of Optimistic Transactions: {}"
              .format(self.opt_tx_count))
        print("  Committed: {}".format(self.opt_tx_committed))
        print("  Aborted: {}".format(self.opt_tx_aborted))
        print("======================")
        print("Number of Snapshot Transactions: {}"
              .format(self.snap_tx_count))
        print("  Committed: {}".format(self.snap_tx_committed))
        print("  Aborted: {}".format(self.snap_tx_aborted))
        print("======================")
        print("Number of transactional")
        print("  Reads: {}".format(self.tx_read_count))
        print("  Writes: {}".format(self.tx_write_count))
        print("  Remove: {}".format(self.tx_rm_count))
        print("======================")
        print("Number of Non-transactional")
        print("  Reads: {}".format(self.read_count))
        print("  Writes: {}".format(self.write_count))
        print("  Remove: {}".format(self.rm_count))
        print("======================")

    def __add__(self, other) -> ClientReport:
        assert (self.client_id == other.client_id)
        new_client_report = ClientReport(self.client_id)

        new_client_report.liveness_success = (
                self.liveness_success and other.liveness_success)

        new_client_report.operation_counters = {}
        new_client_report.snap_tx_counters = {}
        new_client_report.opt_tx_counters = {}

        for op_type in self.operation_counters:
            new_client_report.operation_counters[op_type] = (
                    self.operation_counters[op_type] +
                    other.operation_counters[op_type])

        for state in self.snap_tx_counters:
            new_client_report.snap_tx_counters[state] = (
                    self.snap_tx_counters[state] +
                    other.snap_tx_counters[state])

        for state in self.opt_tx_counters:
            new_client_report.opt_tx_counters[state] = (
                    self.opt_tx_counters[state] +
                    other.opt_tx_counters[state])

        return new_client_report
