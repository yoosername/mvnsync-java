package app.maven.providers;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.eclipse.aether.connector.wagon.WagonProvider;

public class ManualWagonProvider implements WagonProvider {

    public Wagon lookup( String roleHint )
        throws Exception {
        if ( "http".equals( roleHint ) ) {
            return new HttpWagon();
        }
        return null;
    }

    public void release( Wagon wagon ) {

    }

}
