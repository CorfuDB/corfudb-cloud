from enum import Enum


class TestStatus(Enum):
    success = (1, 'Pass', True)
    failure = (2, 'Fail', False)

    @classmethod
    def from_string(cls, s):
        for status in cls:
            if status.value[1] == s:
                return status
        raise ValueError(cls.__name__ + ' has no value matching "' + s + '"')

    @classmethod
    def from_bool(cls, s):
        for status in cls:
            if status.value[2] == s:
                return status
        raise ValueError(cls.__name__ + ' has no value matching "' + s + '"')

    def __str__(self):
        return self._value_[1]

    def __bool__(self):
        return self._value_[2]
