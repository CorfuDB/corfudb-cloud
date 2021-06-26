from correctness.operation import Operation
from correctness.state import State


class WriteOperation(Operation):
    """ Operation looks like this:
        map_id:key=value
    """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)
        op_elements = self.op.split(",")
        self.op = op_elements[0]

        # In some cases we have a version as well
        if len(op_elements) == 2:
            self.version = int(op_elements[1])

        (self.map_id, write_op) = self.op.strip().split(":")
        (self.key_id, self.value) = write_op.strip().split("=")

    def demultiplex(self, demultiplexer_state, line: str):
        demultiplexer_state.write_operation(self.client_id, self.thread, self.map_id, self.key_id, line)

    def add_to_history(self, state: State):
        """ For write, it will just update the map with new current value.
            If anything happens, raise an Exception

        Args:
           state (obj): global state of maps

        Returns:
            None

        """
        # Version comes from context
        self.version = state.get_thread_latest_version(self.thread)

        key_state = state.get(self.map_id, self.key_id)
        key_state.put(self.value, self.version,
                      state.get_oldest_tx_version(),
                      self.thread, self.client_id)
