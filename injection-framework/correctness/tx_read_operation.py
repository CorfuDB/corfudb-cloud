from correctness.read_inconsistency import ReadInconsistency
from correctness.read_operation import ReadOperation


class TxReadOperation(ReadOperation):
    """ Operation look like this:
        map_id:key=value
    """

    def __init__(self, line: str, client_id):
        super().__init__(line, client_id)
        self.tx_id = self.thread

    def add_to_history(self, state):
        """ For tx_read, we need to assert that what we read is or
            the latest write we saw in the transaction or the latest write
            before the transaction begun

            If anything happens, raise an Exception

        Args:
           state (obj): all the maps of the run

        Returns:
            None

        """
        state.read_count += 1

        current_tx = state.get_tx_state(self.tx_id)
        current_tx.set_version(self.version)

        # If the key was updated during the transaction,
        # we keep the value because we don't keep all tx states
        # during the run
        self.key_from_tx = False
        try:
            self.value_from_tx = current_tx.get(self.map_id, self.key_id,
                                                self.version)
            self.key_from_tx = True
        except KeyError:
            pass

        # Safety
        assert (current_tx.version == self.version)

        state.read_buffer.append(self)

    def demultiplex(self, demultiplexer_state, line):
        demultiplexer_state.write_transaction_operation(self.client_id,
                                                        self.tx_id,
                                                        self.map_id,
                                                        self.key_id,
                                                        line)

    def verify(self, state):
        """ Verify if the read soesn't violate consistency

            If the key was updated during the transaction, we
            will have it's value in value_from_tx. Otherwise,
            we read the value from the global history

        Args:
           state (obj): global history of the run

        Returns:
            None

        """
        if self.key_from_tx:
            expected_value = self.value_from_tx
        else:
            key_state = state.get(self.map_id, self.key_id)
            expected_value = key_state.get_at_version(self.version)

        try:
            # print("assert get_at_version(%s) (%s) == read_value (%s)" % (
            #     self.version, self.read_value, self.value))
            assert expected_value == self.value
        except AssertionError:
            state.incorrect_read += 1
            inconsistency = ReadInconsistency(self, expected_value,
                                              self.key_from_tx, state, True)
            inconsistency.log_report()
