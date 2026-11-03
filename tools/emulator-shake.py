#!/usr/bin/env python3
"""在 Android 模拟器里模拟「摇一摇」，用于手工测试撤销弹窗。

模拟器控制台的 `adb emu sensor set acceleration x:y:z` 一次只能设一个值，
而摇动判定看的是「相邻采样之间的变化」——手动一条条设太慢，凑不够命中次数。
这个脚本直接连模拟器控制台 socket，连续灌一串 42 / 1 交替的加速度，模拟真实甩动。

用法（需先启动模拟器）：
    python tools/emulator-shake.py          # 默认 15 组脉冲
    python tools/emulator-shake.py 20 0.03  # 20 组，间隔 30ms

注意事项：
- 只能在模拟器上跑，真机没有这个控制台。
- 状态栏上要有可撤销操作（收藏、评价、答题…）才会响应，且需在 45 秒有效期内。
- 采样率约 20ms（SENSOR_DELAY_GAME），50ms 间隔即相当于 2–3Hz 的抖动。
"""

import os
import socket
import sys
import time

CONSOLE_HOST = "127.0.0.1"
CONSOLE_PORT = 5554


def main() -> int:
    cycles = int(sys.argv[1]) if len(sys.argv) > 1 else 15
    delay = float(sys.argv[2]) if len(sys.argv) > 2 else 0.05

    token = ""
    token_path = os.path.expanduser("~/.emulator_console_auth_token")
    if os.path.exists(token_path):
        with open(token_path, encoding="utf-8") as f:
            token = f.read().strip()

    sock = socket.create_connection((CONSOLE_HOST, CONSOLE_PORT), timeout=5)
    sock.settimeout(0.3)

    def drain() -> None:
        try:
            while sock.recv(4096):
                pass
        except Exception:
            pass

    time.sleep(0.2)
    drain()
    if token:
        sock.sendall(f"auth {token}\n".encode())
        time.sleep(0.3)
        drain()

    for _ in range(cycles):
        sock.sendall(b"sensor set acceleration 0:42:0\n")
        time.sleep(delay)
        sock.sendall(b"sensor set acceleration 0:1:0\n")
        time.sleep(delay)

    sock.sendall(b"sensor set acceleration 0:9.8:0\n")
    time.sleep(0.2)
    drain()
    sock.close()
    print(f"已发送 {cycles} 组摇动脉冲")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
