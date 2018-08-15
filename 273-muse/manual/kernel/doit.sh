#!/usr/bin/env bash
# doit.sh

set -o errexit
set -o nounset
set -o pipefail

set -x

gcc --std=c99 -Werror -Wall -Wextra -m32 -O2 -o./../../build/kernel-test -x c test.c

./../../build/kernel-test

echo "+OK (test)"
