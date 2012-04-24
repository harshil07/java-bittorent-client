/**
 * 
 */

/**
 * @author Robert S. Moore II
 *
 */
public final class BencodingException extends Exception
{
    /**
     * For serialization of this object.
     */
    /*
     * TODO: Make sure this is updated whenever the class is changed.
     */
    private static final long serialVersionUID = -4829433031030292728L;
    
    /**
     * The message to display regarding the exception.
     */
    private final String message;
    
    /**
     * Creates a new BencodingException with a blank message.
     */
    public BencodingException()
    {
        this.message = null;
    }
    
    /**
     * Creates a new BencodingException with the message provided.
     * @param message the message to display to the user.
     */
    public BencodingException(final String message)
    {
        this.message = message;
    }
    
    /**
     * Returns a string containing the exception message specified during
     * creation.
     */
    @Override
    public final String toString()
    {
        return "Bencoding Exception:\n"+(message == null ? "" : message);
    }
}
