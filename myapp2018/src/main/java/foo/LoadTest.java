package foo;

import java.io.IOException;
import java.util.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.*;

@WebServlet(name = "loadtest", urlPatterns = { "/loadtest" })
public class LoadTest extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

        final int USER_NUMBER = 100;
        final double AVERAGE_NUMBER = 30;
        final String FOLLOWED_USER = "mainUser";
        final String FOLLOWER_USER = "user0";

        long startTime = 0;
        long delta = 0;
        long endTime = 0;

        String strResponse = "";






        // Create users
        /*ArrayList<String> mainUserFollowers = new ArrayList<>();
        for(int i=0 ; i<USER_NUMBER ; i++) {


            Entity e = new Entity("User");
            e.setProperty("name", "user" + i);
            mainUserFollowers.add("user" + i);
            // Add subscribers
            e.setProperty("subscribers", new ArrayList<String>());
            // Add subscriptions
            ArrayList<String> subs = new ArrayList<>();
            subs.add(FOLLOWED_USER);
            e.setProperty("subscriptions", subs);
            e.setProperty("date", new Date());
            datastore.put(e);

        }

        // Add the created users in the mainUser followers
        Query q = new Query("User")
                .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        result.get(0).setProperty("subscribers", mainUserFollowers);
        datastore.put(result.get(0));





        ///////////////////////
        ///////////////////////
        // How long it takes to post of message if followed by 10, 100 and 500 followers? (average on 30 measures)

        for(int j=0 ; j<AVERAGE_NUMBER ; j++) {
            startTime = System.currentTimeMillis();

            // Retrieve the followee
            q = new Query("User")
                    .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
            pq = datastore.prepare(q);
            result = pq.asList(FetchOptions.Builder.withDefaults());
            Entity followeeEntity = result.get(0);
            // Creates Post entity with its User as parent, and let the Appengine generate the key
            Entity e = new Entity("Post", followeeEntity.getKey());
            e.setProperty("name", FOLLOWED_USER);
            String demoHashtags = "hashtagA, hashtagB, hashtagC";
            // Regex splits the string on a delimiter defined as: zero or more whitespace, a literal comma, zero or more whitespace
            ArrayList<String> hashtagList = new ArrayList<>(Arrays.asList(demoHashtags.split("\\s*,\\s*")));
            e.setProperty("hashtag", hashtagList);
            String demoImageUrl = "http://localhost:8080/_ah/img/keuCZWbcxUZZT7BLk42bKw";
            e.setProperty("image", demoImageUrl);
            e.setProperty("date", new Date());
            // TODO replace timestamp with rand value because we are inserting more than one post per millisec
            //e.setProperty("timestamp", -System.currentTimeMillis()/1000);
            e.setProperty("timestamp", new Random().nextLong());
            datastore.put(e);

            endTime = System.currentTimeMillis();
            delta += endTime-startTime;

        }

        strResponse += delta/AVERAGE_NUMBER + "\n";
        System.out.println("Post load test average on " + AVERAGE_NUMBER + ": " + delta/AVERAGE_NUMBER);*/










        ///////////////////////
        ///////////////////////
        ///////////////////////
        ///////////////////////
        // How long it takes to retrieve the last 10, 100 and 500 last messages ? (average of 30 measures)

        // Retrieve the followee
        /*Query q = new Query("User")
                .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, FOLLOWED_USER));
        PreparedQuery pq = datastore.prepare(q);
        List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
        Entity followeeEntity = result.get(0);

        // Create the posts
        for(int i=0 ; i<USER_NUMBER ; i++) {
            // Creates Post entity with its User as parent, and let the Appengine generate the key
            Entity e = new Entity("Post", followeeEntity.getKey());
            e.setProperty("name", FOLLOWED_USER);
            String demoHashtags = "hashtagA, hashtagB, hashtagC";
            // Regex splits the string on a delimiter defined as: zero or more whitespace, a literal comma, zero or more whitespace
            ArrayList<String> hashtagList = new ArrayList<>(Arrays.asList(demoHashtags.split("\\s*,\\s*")));
            e.setProperty("hashtag", hashtagList);
            String demoImageUrl = "http://localhost:8080/_ah/img/keuCZWbcxUZZT7BLk42bKw";
            e.setProperty("image", demoImageUrl);
            e.setProperty("date", new Date());
            // TODO replace timestamp with rand value because we are inserting more than one post per millisec
            //e.setProperty("timestamp", -System.currentTimeMillis()/1000);
            e.setProperty("timestamp", new Random().nextLong());
            datastore.put(e);
        }

        delta = 0;
        for(int j=0 ; j<AVERAGE_NUMBER ; j++) {
            startTime = System.currentTimeMillis();

            q = new Query("User").setFilter(new Query.FilterPredicate("subscribers", Query.FilterOperator.EQUAL, FOLLOWER_USER)).setKeysOnly();
            pq = datastore.prepare(q);
            List<Entity> userIds = pq.asList(FetchOptions.Builder.withDefaults());
            ArrayList<Entity> posts = new ArrayList<>();
            for(Entity userId : userIds){
                q = new Query("Post").setAncestor(userId.getKey());
                pq = datastore.prepare(q);
                List<Entity> userPosts = pq.asList(FetchOptions.Builder.withDefaults());
                posts.addAll(userPosts);
            }
            Collections.sort(posts, (o1, o2) -> (int) (((Long) o1.getProperty("timestamp")) - ((Long) o2.getProperty("timestamp"))));

            endTime = System.currentTimeMillis();
            delta += endTime-startTime;
        }

        strResponse += delta/AVERAGE_NUMBER + "\n";
        System.out.println("Get Post load test average on " + AVERAGE_NUMBER + ": " + delta/AVERAGE_NUMBER);*/






        ///////////////////////
        ///////////////////////
        ///////////////////////
        ///////////////////////
        // How many “likes” can you do per second ?? (average on 30 measures)

        delta = 0;

        /*Thread[] th=new Thread[2];
        for (int i=0;i<th.length;i++) {
            th[i]=ThreadManager.createThreadForCurrentRequest(new Runnable()  {
                public void run() {
                    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
                    for (int j=0;j<10;j++) {

                    }
                }
            });
            th[i].start();
        }

        for (Thread thread : th) {
            try {
                thread.join();
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }*/


        response.getWriter().print(strResponse);
    }
}