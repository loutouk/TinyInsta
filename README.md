# WebAndCloud

## About
[TinyInsta](https://mystical-app-220509.appspot.com/) is a scalable Instagram like toy app to get familiar with Google Appengine and Google Datastore.

## Preview

### User page

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/previewA.png)

### Hashtag page

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/previewB.png)

## Load Analysis
The action of POSTing a Post is logarithmic with the number of elements to be posted because it is decoupled from the number of followers. It must even be considered as constant.

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/post.png)



The action of GETing some Post is linear with the number of items we want to access.

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/get.png)


The load analysis on the "like" button is hard to do because we would need a lot of powerful machines to simulate the contention. Anyway, the contention problem can easily be adressed by increasing the number of shards for each like counter. The default value of LIKE_COUNTER_MAX_SHARD is 20. But this value can be increased at any time in a transparent manner if needed.

## DataStore Kinds
### User

Data denormalization on subscribers and followers to increase performance. No need to compute the followers from the subscribers or vice versa. We directly access the information we need.

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/user.PNG)

### Post

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/posts.PNG)

### LikeShard

Sharded counter to automatically absorb the load. 
A numeric counter is used to avoid the performance cost of counting the number of likes with a count() operation.
Data denormalization is used on the property UserAndPostid. It is a home made composite index to quickly link a post and its user's like. It saves us from having a combinatorial explosion (userIds * postIds) because an element is created only when needed (when a like is performed).

![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/likeshard.PNG)


## API Explorer
[Explore the API](https://mystical-app-220509.appspot.com/_ah/api/explorer)

## Install and Run
* [See Java Appengine Quickstart](https://cloud.google.com/appengine/docs/standard/java/quickstart)
* git clone https://github.com/loutouk/TinyInsta.git
* cd TinyInsta/myapp2018/
* mvn appengine:deploy (deploy on Google's servers)
* mvn appengine:run (deploy locally)
* gcloud app browse (or localhost if local deployment)
