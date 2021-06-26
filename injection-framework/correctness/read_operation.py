from correctness.operation import Operation
from correctness.read_inconsistency import ReadInconsistency
from correctness.state import State


class ReadOperation(Operation):
    """ Operation looks like this:
        map_id:key=value[, version]
    """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)
        op_elements = self.op.split(",")
        self.op = op_elements[0]

        # In some cases we have a version as well
        if len(op_elements) == 2:
            self.version = int(op_elements[1])

        (self.map_id, read_op) = self.op.strip().split(":")
        (self.key_id, self.value) = read_op.strip().split("=")

        # Change the null in None
        if self.value == "null":
            self.value = None

    def add_to_history(self, state: State) -> None:
        """ Add read to global history """
        state.read_count += 1
        # Version comes from context
        self.version = state.get_thread_latest_version(self.thread)

        state.read_buffer.append(self)

    def demultiplex(self, demultiplexer_state, line):
        demultiplexer_state.write_operation(self.client_id, self.thread,
                                            self.map_id, self.key_id, line)

    def verify(self, state: State) -> None:
        """ Verify if the read doesn't violate consistency

            For read, we need to assert that what we read is the
            value that was written at the current version of the
            object.

            If anything happens, raise an Exception

        Args:
           state (obj): global history of the run

        Returns:
            None

        """
        key_state = state.get(self.map_id, self.key_id)
        key_at_version = key_state.get_at_version(self.version)

        try:
            assert self.value == key_at_version
        except AssertionError:
            state.incorrect_read += 1
            inconsistency = ReadInconsistency(self, key_at_version,
                                              False, state, False)
            inconsistency.log_report()
