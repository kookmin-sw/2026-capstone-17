#!/usr/bin/env python3
import argparse
import base64
import hashlib
import hmac
import json
import time


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a local HS512 JWT for focus-server.")
    parser.add_argument("--secret-b64", required=True, help="Base64-encoded JWT secret")
    parser.add_argument("--member-id", required=True, help="Member ID to put into sub")
    parser.add_argument("--name", default="local-user", help="Display name claim")
    parser.add_argument("--expires-in-seconds", type=int, default=60 * 60 * 3)
    args = parser.parse_args()

    header = {"alg": "HS512", "typ": "JWT"}
    payload = {
        "sub": args.member_id,
        "iss": "focus",
        "name": args.name,
        "exp": int(time.time()) + args.expires_in_seconds,
    }

    secret = base64.b64decode(args.secret_b64)
    unsigned = (
        f"{b64url(json.dumps(header, separators=(',', ':')).encode())}."
        f"{b64url(json.dumps(payload, separators=(',', ':')).encode())}"
    )
    signature = hmac.new(secret, unsigned.encode(), hashlib.sha512).digest()
    print(f"{unsigned}.{b64url(signature)}")


if __name__ == "__main__":
    main()
