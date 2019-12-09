package foo;

import java.util.ArrayList;
import java.util.Date;

/**
 * Used to cast Google Appengine Post entities into Java classes in order to sort them with a Java TreeSet
 * Insertion is in O(log(n)) versus a sort on the Post entities in O(nlog(n))
 * @see foo.PostEndpoint#getSubscriberPost(String)
 *
 * in the Endpoints Frameworks documentation, entity types are synonymous with Java Beans. The classes that you define for use in your API must
 * 		have a public constructor that takes no arguments
 * 		control access to private properties using getters and setters. Additionally, each setter must take only one parameter
 *
 */
public class Post implements Comparable{
    public String name;
    public ArrayList<String> hashtag;
    public String image;
    public Date date;
    public Long timestamp;

    public Post(){

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArrayList<String> getHashtag() {
        return hashtag;
    }

    public void setHashtag(ArrayList<String> hashtag) {
        this.hashtag = hashtag;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Used for the natural ordering in the Treeset
     *
     * @param o other Post
     * @return 1 if bigger, -1 if smaller, 0 if equal
     */
    @Override
    public int compareTo(Object o) {
        Post other = (Post) o;
        return other.timestamp > timestamp ? 1 : (other.timestamp < timestamp ? -1 : 0);
    }
}
