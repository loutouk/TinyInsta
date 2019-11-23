package foo;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;

@WebServlet(name = "LoadTest", urlPatterns = { "/loadtest" })
public class LoadTest extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Entity e = new Entity("Count", "c1");
        e.setProperty("val", 0);
        datastore.put(e);

        try {
            response.getWriter().println("initial value:"+datastore.get(e.getKey()).getProperty("val")+"<br>");
        } catch (EntityNotFoundException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }

        Thread[] th=new Thread[2];
        for (int i=0;i<th.length;i++) {
            th[i]=ThreadManager.createThreadForCurrentRequest(new Runnable()  {
                public void run() {
                    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
                    for (int j=0;j<10;j++) {
                        try {
                            Entity c = datastore.get(e.getKey());
                            Long v=(Long)c.getProperty("val");
                            Thread.sleep(100);
                            c.setProperty("val", v+1);
                            response.getWriter().print("Thread:"+Thread.currentThread()+",val:"+v+"<br>");
                            ds.put(c);
                        } catch (EntityNotFoundException | InterruptedException | IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
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
        }

        try {
            response.getWriter().print("final value:"+datastore.get(e.getKey()).getProperty("val"));
        } catch (EntityNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }
}