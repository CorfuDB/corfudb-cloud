from collections import deque

from correctness.key_state import KeyState
from correctness.tx_state import TxState


class State:
    """ This contains state of all the keys in the run
    """

    def __init__(self):
        self.key_states = {}
        self.transactions = {}
        self.tx_history = deque()

        self.thread_latest_versions = {}

        # To see how big it is in memory
        self.read_buffer = []
        self.read_count = 0
        self.incorrect_read = 0

        self.maps = set()
        self.keys = set()

    def get(self, map_id: str, key_id: str) -> KeyState:
        """ get this key state

        Args:
           map_id (str): map id
           key_id (str): key id

        Returns:
            obj: KeyState

        """
        compound_key = (map_id, key_id)
        self.maps.add(map_id)
        self.keys.add(key_id)

        # If we don't have an entry yet, create one
        if compound_key not in self.key_states:
            self.key_states[compound_key] = KeyState(map_id, key_id)

        return self.key_states[compound_key]

    def get_oldest_tx_version(self) -> int:
        if len(self.tx_history) == 0:
            return -1

        return self.tx_history[0].version

    def get_tx_state(self, tx_id):
        return self.transactions[tx_id]

    def start_tx(self, client_id, tx_id, tx_type, version):
        # print("start_tx: " + tx_id)
        tx = TxState(client_id, tx_id, tx_type, version)
        self.transactions[tx_id] = tx
        self.tx_history.append(tx)

    def commit_tx(self, tx_id, version) -> None:
        self.transactions[tx_id].commit(self, version)
        self.transactions[tx_id].terminated = True
        self.transactions.pop(tx_id)

    def abort_tx(self, tx_id) -> None:
        self.transactions[tx_id].terminated = True
        self.transactions.pop(tx_id)

    def trimm_tx_history(self) -> None:
        while self.tx_history[0].terminated:
            self.tx_history.popleft()

    def get_thread_latest_version(self, thread_id) -> int:
        if thread_id not in self.thread_latest_versions:
            return -1

        return self.thread_latest_versions[thread_id]

    def update_thread_latest_version(self, thread_id, version):
        self.thread_latest_versions[thread_id] = version

    def verify(self) -> None:
        """ Process all the read and verify consistency

        Args:
           self

        Returns:
            None

        """
        for read in self.read_buffer:
            read.verify(self)
