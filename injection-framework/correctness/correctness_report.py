from __future__ import annotations

from utils.test_status import TestStatus
from utils.utils import print_color_title


class CorrectnessReport:
    """Contains report data from a correctness verification run"""

    def __init__(self,
                 correctness_success: bool, map_count: int, key_count: int, inconsistencies_count: int,
                 checkpoint_count: int, client_reports: dict):
        """

        Args:
           correctness_success (bool): if the correctness was successful (no inconsistency)
           map_count (int): count of map used in the run
           key_count (int): count of key used in the run
           inconsistencies_count (int): count of inconsistencies found
           checkpoint_count (int): count of checkpoints
           client_reports (dict): report on type of operations/client

        """
        self.map_count = map_count
        self.key_count = key_count
        self.inconsistencies_count = inconsistencies_count
        self.client_reports = client_reports
        self.checkpoint_count = checkpoint_count
        self.inconsistencies_percentage = self.compute_inconsistencies_percentage()
        self.operation_count = self.compute_operation_count()
        self.exception_count = None
        self.correctness_success = correctness_success
        self.liveness_success = self.get_clients_liveness()

        # Overwrite liveness success if client was not able
        # to progress, 0 operations performed on 0 maps over 0 keys..
        if self.operation_count == 0 and map_count == 0 and key_count == 0:
            self.liveness_success = False

        # Compute Test Status based on correctness success and liveness success
        self.status: TestStatus = self.compute_test_status()

    def compute_test_status(self) -> TestStatus:
        if self.correctness_success and self.liveness_success:
            return TestStatus.success
        else:
            return TestStatus.failure

    def compute_inconsistencies_percentage(self) -> int:
        # get read count
        total_read_count = 0
        for client_report in self.client_reports.values():
            total_read_count += (client_report.tx_read_count +
                                 client_report.read_count)

        if total_read_count == 0:
            return 0

        return int(self.inconsistencies_count / total_read_count) * 100

    def get_clients_liveness(self) -> bool:
        success = True
        for client_report in self.client_reports.values():
            success = success and client_report.liveness_success
        return success

    def compute_operation_count(self) -> int:
        count = 0
        for client_id, report in self.client_reports.items():
            count += report.operation_count
        return count

    def add_exceptions_count(self, exception_count_dict):
        self.exception_count = exception_count_dict

    def print_report(self) -> None:
        print_color_title("Scenario report")
        print("Correctness success: {}".format(self.correctness_success))
        print("Liveness success: {}".format(self.liveness_success))
        print("Number of maps: {}".format(self.map_count))
        print("Number of keys/map: {}".format(self.key_count))
        print("Number of operations: {}".format(self.operation_count))
        print("Number of Inconsistencies: {}".format(self.inconsistencies_count))
        print("Inconsistency percentage: {} %".format(self.inconsistencies_percentage))
        if self.exception_count is not None:
            print("Exceptions counts:")
            for exception, count in self.exception_count.items():
                print("  {}: {}".format(exception, count))

        for client_id, client_report in self.client_reports.items():
            client_report.print_report()

    def __add__(self, other) -> CorrectnessReport:
        success = self.correctness_success and other.correctness_success
        # should be the same
        key_count = self.key_count
        map_count = self.map_count + other.map_count

        inconsistencies_count = (self.inconsistencies_count + other.inconsistencies_count)
        checkpoint_count = (self.checkpoint_count + other.checkpoint_count)

        client_reports = {}

        for cid in list(self.client_reports) + list(other.client_reports):
            if cid in self.client_reports and other.client_reports:
                client_reports[cid] = (self.client_reports[cid] + other.client_reports[cid])

            elif cid in self.client_reports:
                client_reports[cid] = self.client_reports[cid]

            elif id in other.client_reports:
                client_reports[cid] = other.client_reports[cid]

        return CorrectnessReport(success, map_count, key_count, inconsistencies_count, checkpoint_count, client_reports)
