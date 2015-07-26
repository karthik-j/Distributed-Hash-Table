# Distributed-Hash-Table
CS586 Distributed Systems - Spring 2015, UB
Programming Assignment 3

I designed a simple DHT based on Chord as a part of academic tasks. Although the design is based on Chord, it is a simplified version of Chord; we didn't need to implement finger tables and finger-based routing.The three things I implemented were: 1) ID space partitioning/re-partitioning, 2) Ring-based routing, and 3) Node joins.

The content provider contains all the DHT functionalities and support insert and query operations. Thus, by running multiple instances of my app, all content provider instances will form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.
