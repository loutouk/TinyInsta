# webandcloud

## About
TinyInsta is an Instagram like toy app to get familiar with Google Appengine and Google Datastore.


## Load Analysis
Post posts action is logarithmic with the number of item to post because decoupled from the number of followers
It should even be considered constant
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/post.png)

Get posts action is linear with the number of items we want to access
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/get.png)

The load analysis on the "like" button is hard to do because we would need a lot of powerful machines to simulate the contention. Anyway, the contention problem can be easily adressed by increasing the number of shards for each like counter. The default value of LIKE_COUNTER_MAX_SHARD is 20. But this value can be increased at any time in a transparent manner if needed.

## DataStore Kinds
### User
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/user.png)
### Post
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/posts.png)
### LikeShard
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/likeshard.png)


## API Explorer
[Explore the API](https://mystical-app-220509.appspot.com/_ah/api/explorer)

## Install and Run
* [Java Appengine Quickstart](https://cloud.google.com/appengine/docs/standard/java/quickstart)
* git clone https://github.com/loutouk/TinyInsta.git
* cd webandcloud/myapp2018/
* mvn appengine:deploy
* gcloud app browse
