class TxState:
    """ State of transaction """

    def __init__(self, client_id, tx_id, tx_type, version: int):
        self.tx_id = tx_id
        self.tx_type = tx_type
        self.client_id = client_id

        # This is the version of first read
        self.version = -1

        self.start_tx_version = version

        self.values = {}
        self.terminated = False

    def set_version(self, version) -> None:
        if self.version == -1:
            self.version = version

    def put(self, map_id, key_id, value, version) -> None:
        # if first put/get initialize version
        self.set_version(version)

        self.values[(map_id, key_id)] = value

    def get(self, map_id, key_id, version):
        # if first put/get initialize version
        self.set_version(version)

        return self.values[(map_id, key_id)]

    def commit(self, state, commit_version):
        for (map_id, key_id), value in self.values.items():
            key_state = state.get(map_id, key_id)
            key_state.commit(value, commit_version,
                             state.get_oldest_tx_version(),
                             self.version, commit_version,
                             self.tx_id, self.client_id)

        self.terminated = True
