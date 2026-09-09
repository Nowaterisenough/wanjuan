import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from source_audit_proxy import PROXY_KEYS, restore_proxy, snapshot_proxy


class ProxyRestorationTest(unittest.TestCase):
    def test_snapshot_preserves_missing_and_empty_values(self):
        with patch("source_audit_proxy.subprocess.check_output", return_value=
                   "global_http_proxy_host=127.0.0.1\nglobal_http_proxy_port=7890\n"
                   "global_http_proxy_exclusion_list=\nglobal_proxy_pac_url=\nunrelated=value\n"):
            state = snapshot_proxy(["adb"])
        self.assertIsNone(state["http_proxy"])
        self.assertEqual(state["global_http_proxy_exclusion_list"], "")
        self.assertEqual(set(state), set(PROXY_KEYS))

    def test_restore_refreshes_live_proxy_before_removing_legacy_key(self):
        state = dict.fromkeys(PROXY_KEYS)
        state.update(global_http_proxy_host="127.0.0.1", global_http_proxy_port="7890",
                     global_http_proxy_exclusion_list="", global_proxy_pac_url="")
        with patch("source_audit_proxy.subprocess.run") as run:
            restore_proxy(["adb"], state)
        calls = [call.args[0] for call in run.call_args_list]
        self.assertEqual(calls[0][-2:], ["http_proxy", "127.0.0.1:7890"])
        self.assertEqual(calls[1][-3:], ["delete", "global", "http_proxy"])
        self.assertIn(["adb", "shell", "settings", "put", "global", "global_proxy_pac_url", "''"], calls)

    def test_no_original_proxy_clears_temporary_live_proxy(self):
        with patch("source_audit_proxy.subprocess.run") as run:
            restore_proxy(["adb"], dict.fromkeys(PROXY_KEYS))
        self.assertEqual(run.call_args_list[0].args[0][-1], ":0")
        self.assertEqual(len(run.call_args_list), 6)


if __name__ == "__main__":
    unittest.main()
