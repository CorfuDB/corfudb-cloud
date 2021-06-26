from correctness.key_state import get_short_value

READ_AFTER_WRITE_STR = "read after write (value written in same tx)"
DEFAULT_READ_STR = "value read comes from other operation"


class ReadInconsistency:
    """Represent a read inconsistency"""

    file_already_created = False

    def __init__(self, read_op, expected_value: str, read_after_write: bool, state, in_tx: bool):
        """
        Args:
           read_op (obj): inconsistent operation
           expected_value (str): value that should have been read
           read_after_write (bool): if we read value written in same tx
           state (obj): global state of keys history
           in_tx (bool): if the read happen in tx or not
        """
        # Getting fields from read_op
        self.map_id = read_op.map_id
        self.key_id = read_op.key_id
        self.version = read_op.version
        self.client_id = read_op.client_id
        self.thread = read_op.thread
        self.value = read_op.value
        self.timestamp = read_op.timestamp

        self.expected_value = expected_value
        self.read_after_write = read_after_write
        self.in_tx = in_tx
        self.history_table = state.get(read_op.map_id, read_op.key_id).generate_history()

    def generate_inconsistency_report(self) -> str:
        type_of_read = (READ_AFTER_WRITE_STR if self.read_after_write
                        else DEFAULT_READ_STR)

        tx_suffix = "(tx)" if self.in_tx else "(non-tx)"

        value_str = ("map: %s, key: %s\nexpected value: %s, read_value: %s" %
                     (self.map_id, self.key_id,
                      get_short_value(self.expected_value),
                      get_short_value(self.value)))

        return "\n".join(
            ["\nAssertionError({}) {}".format(type_of_read, tx_suffix),
             value_str,
             "Version: {}".format(self.version),
             "Timestamp: {}".format(self.timestamp),
             "Thread: {}".format(self.thread),
             "Client: {}".format(self.client_id)]) + "\n"

    def print_report(self) -> None:
        print(self.generate_inconsistency_report())
        print(self.history_table)

    def log_report(self) -> None:
        # Truncate the file only if it was not opened yet
        file_mode = "a" if ReadInconsistency.file_already_created else "w"

        # Correctness inconsistencies report
        inconsistencies_report = "/tmp/inconsistencies_report"

        with open(inconsistencies_report, file_mode) as f:
            ReadInconsistency.file_already_created = True
            f.write(self.generate_inconsistency_report())
            f.write(self.history_table.__str__())
            f.write("\n")
