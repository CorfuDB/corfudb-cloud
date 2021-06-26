from correctness.operation import Operation
from correctness.state import State


class RemoveOperation(Operation):
    """ Operation looks like this:
        map_id:key
    """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)

        op_elements = self.op.split(",")
        self.op = op_elements[0]

        # In some cases we have a version as well
        if len(op_elements) == 2:
            self.version = int(op_elements[1])

        (self.map_id, rm_op) = self.op.strip().split(":")
        self.key_id = rm_op

    def demultiplex(self, demultiplexer_state, line):
        demultiplexer_state.write_operation(self.client_id,
                                            self.thread,
                                            self.map_id,
                                            self.key_id,
                                            line)

    def add_to_history(self, state: State) -> None:
        """ For remove, it will just put the current value to None """
        # Version comes from context
        self.version = state.get_thread_latest_version(self.thread)

        key_state = state.get(self.map_id, self.key_id)
        key_state.put(None, self.version, state.get_oldest_tx_version(), self.thread)
