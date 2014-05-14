Welcome to primes play application
Reactive implementation
=====================================

mongod --dbpath data/db/  --logpath=mongod.log  --port 27017 --rest --fork

mongo
> db.values.getIndexes()
> db.values.createIndex({value:1})
> db.values.createIndex({isPrime:1, nth:1})
>

play
> run
