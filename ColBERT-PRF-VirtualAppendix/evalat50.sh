#!/bin/bash

if [ $# -lt 1 ]
then
        echo "Usage: $0 <directory>"
        exit
fi

DIR=$1

cd $DIR
gunzip *gz

cat *2019.res >> all.res
cat *2020.res >> all.res
trec_eval -m all_trec -M50 ~/research/common/msmarco/data/trecdl1920.qrels all.res | egrep -w "map|ndcg"

rm all.res


