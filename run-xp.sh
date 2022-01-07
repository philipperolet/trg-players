#!/bin/bash
set -xe

git pull

xp_clj_name=$1
git_commit=`git rev-parse --short HEAD`
shift 1
stderr_file="xps/results/$xp_clj_name-$1-$2-$3-$git_commit.err"
output_file="xps/results/$xp_clj_name-$1-$2-$3-$git_commit.out"

echo "Create results dir if it doesn't exist"
[ -d "xps/results" ] || mkdir xps/results

echo "Check git is clean, no rogue modif for xp"
[ -z "`git status --porcelain`" ]

echo "Start xp"
lein run -m $xp_clj_name $* 2>$stderr_file >$output_file </dev/null &
tail -f $stderr_file $output_file
