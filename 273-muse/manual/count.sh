#!/bin/bash

( cat guide.txt | egrep 'opcodes' | egrep -o '[0-9]+' | tr '\n' '+'; echo 0 ) | bc -q
