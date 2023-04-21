# lucene-msmarco

This code is distributed in the hope that it'll be useful for IR practitioners and students who want to get started with retrieving documents from a collection and measure effectiveness with standard evaluation metrics. Similar in flavour to its parent project [Luc4IR](https://github.com/gdebasis/luc4ir), this project implemets the functionality to index the MS MARCO passage collection. While you can still do that with the parent project, this project contains a minimalistic implementation.


### Index the MS MARCO passage collection

Download the MS MARCO passage collection file `collection.tsv`, and create a soft link to the file named `data/collection.tsv`.
 
Run the following script to build the index.
```
./index.sh
```
