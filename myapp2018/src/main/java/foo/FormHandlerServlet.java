package foo;

import com.google.appengine.api.blobstore.*;
import com.google.appengine.api.datastore.*;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.text.SimpleDateFormat;


/**
 * handles the creation of a post
 * this is done here and not in the endpoint because the image enctype can not be treated in google app endpoints yet
 * see the cloud endpoints doc about supported types for path and query parameters
 * @see foo.PostEndpoint
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@WebServlet("/my-form-handler")
public class FormHandlerServlet extends HttpServlet {

    /**
     * creates the post entity with the image and its data
     * @param request
     * @param response
     * @throws IOException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Get the data entered by the user.
        String hashtag = request.getParameter("hashtag");
        if(hashtag == null || hashtag.length() < 1){
            hashtag = ""; // Considers hashtag not mandatory
        }
        String name = request.getParameter("name");

        // Get the URL of the image that the user uploaded to Blobstore.
        String imageUrl = getUploadedFileUrl(request, "image");

        // If any of the values is incorrect, we abort
        if(name != null && name.length() > 0 && imageUrl != null && imageUrl.length() > 0){
            // Retrieve the followee
            Query q = new Query("User")
                    .setFilter(new Query.FilterPredicate("name", Query.FilterOperator.EQUAL, name));
            DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
            PreparedQuery pq = datastore.prepare(q);
            List<Entity> result = pq.asList(FetchOptions.Builder.withDefaults());
            if(result.isEmpty()){
                response.sendRedirect("/log"); // No corresponding user, we abort
            }else{
                // There should be only one element in the list because of name uniqueness
                Entity followeeEntity = result.get(0);

                // Create Post entity with its User as parent
                Entity e = new Entity("Post", followeeEntity.getKey());
                e.setProperty("name", name);
                // Regex splits the string on a delimiter defined as: zero or more whitespace, a literal comma, zero or more whitespace
                ArrayList<String> hashtagList = new ArrayList<>(Arrays.asList(hashtag.split("\\s*,\\s*")));
                e.setProperty("hashtag", hashtagList);
                e.setProperty("image", imageUrl);
                SimpleDateFormat pattern = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                e.setProperty("date", pattern.format(new Date()));
                datastore.put(e);
            }

        }

        response.sendRedirect("/log");
    }

    /**
     *
     * @param request
     * @param formInputElementName
     * @return a URL that points to the uploaded file, or null if the user didn't upload a file
     */
    private String getUploadedFileUrl(HttpServletRequest request, String formInputElementName){

        BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
        Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(request);
        List<BlobKey> blobKeys = blobs.get("image");

        // User submitted form without selecting a file, so we can't get a URL. (devserver)
        if(blobKeys == null || blobKeys.isEmpty()) {
            return null;
        }

        // Our form only contains a single file input, so get the first index.
        BlobKey blobKey = blobKeys.get(0);

        // User submitted form without selecting a file, so we can't get a URL. (live server)
        BlobInfo blobInfo = new BlobInfoFactory().loadBlobInfo(blobKey);
        if (blobInfo.getSize() == 0) {
            blobstoreService.delete(blobKey);
            return null;
        }

        // We could check the validity of the file here, e.g. to make sure it's an image file
        // https://stackoverflow.com/q/10779564/873165

        // Use ImagesService to get a URL that points to the uploaded file.
        ImagesService imagesService = ImagesServiceFactory.getImagesService();
        ServingUrlOptions options = ServingUrlOptions.Builder.withBlobKey(blobKey);
        return imagesService.getServingUrl(options);
    }
}
