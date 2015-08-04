#!/bin/sh

LOG=$1

grep "Yongest known timestamp of a node:" "$LOG" > $BASE/out/$country.log
