package foo;

import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * tool class
 * @see foo.FormHandlerServlet#doPost(HttpServletRequest, HttpServletResponse)
 *
 * @author  LASHERME Loic, FILAUDEAU Eloi, BOURSIER Louis
 * @version 1.0
 */
@WebServlet("/blobstore-upload-url")
public class BlobstoreUploadUrlServlet extends HttpServlet {

    /**
     * returns a link to the client form to upload the image for its post
     * @param request
     * @param response
     * @throws IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
        String uploadUrl = blobstoreService.createUploadUrl("/my-form-handler") ;

        response.setContentType("text/html");
        response.getOutputStream().println(uploadUrl);
    }
}
