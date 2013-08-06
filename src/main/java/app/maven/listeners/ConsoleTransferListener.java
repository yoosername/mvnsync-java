package app.maven.listeners;

import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.observers.AbstractTransferListener;

/**
 * A simplistic transfer listener that logs events to the console.
 */
public class ConsoleTransferListener extends AbstractTransferListener
{

	public void transferStarted( TransferEvent transferEvent )
    {
        System.out.print( "  Downloading " + transferEvent.getResource().getName() );
    }

    public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
    {
    }

    public void transferCompleted( TransferEvent transferEvent )
    {
        System.out.println( " - Done" );
    }

}
