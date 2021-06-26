from correctness.operation import Operation


class TxOperation(Operation):
    """ tx_operation looks like this : tx_type, <end|start|aborted> """

    def __init__(self, line, client_id):
        super().__init__(line, client_id)
        self.tx_type = self.op_type

        op_elements = self.op.split(",")
        self.op = op_elements[0]

        # In some cases we have a version as well
        self.version = -1
        if len(op_elements) == 2:
            self.version = int(op_elements[1])

        self.tx_state = self.op
        self.tx_id = self.thread

    def demultiplex(self, demultiplexer_state, line) -> None:
        if self.tx_state == "start":
            demultiplexer_state.add_transaction(self.client_id, self.tx_id, line)
        else:
            demultiplexer_state.finish_transaction(self.client_id, self.tx_id, line)

    def add_to_history(self, state) -> None:
        if self.tx_state == "start":
            state.start_tx(self.client_id, self.tx_id, self.tx_type, self.version)
        elif self.tx_state == "end":
            state.commit_tx(self.tx_id, self.version)
        elif self.tx_state == "aborted":
            state.abort_tx(self.tx_id)
