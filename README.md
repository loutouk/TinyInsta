# webandcloud

## About
TinyInsta is an instagram like toy app to get familiar with Google Appengine and Google Datastore.


## Load Analysis
Post posts action is constant and decoupled from the number of followers
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/post.png)

Get posts action is linear with the number of items we want to access
![alt text](https://github.com/loutouk/TinyInsta/blob/master/myapp2018/data/get.png)

## DataStore Kinds

## Install and Run
* [Java Appengine Quickstart](https://cloud.google.com/appengine/docs/standard/java/quickstart)
* git clone https://github.com/loutouk/TinyInsta.git
* cd webandcloud/myapp2018/
* mvn appengine:deploy
* gcloud app browse
