from correctness.rm_operation import RemoveOperation


class TxRemoveOperation(RemoveOperation):
    """ Operation looks like this:
        map_id:key=value
    """

    def __init__(self, line: str, client_id):
        super().__init__(line, client_id)
        self.tx_id = self.thread

    def demultiplex(self, demultiplexer_state, line) -> None:
        demultiplexer_state.write_transaction_operation(self.client_id, self.tx_id, self.map_id, self.key_id, line)

    def add_to_history(self, state) -> None:
        """ For TxRemove, we will update the corresponding transaction.

        Args:
           state (obj): global state of maps

        Returns:
            None

        """
        tx_state = state.get_tx_state(self.tx_id)
        tx_state.put(self.map_id, self.key_id, None, self.version)
