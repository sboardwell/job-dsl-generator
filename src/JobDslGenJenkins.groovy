import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import jenkins.model.Jenkins

class JobDslGenJenkins {

    static String getUserNamePasswordCredential(def name) {
        def creds = CredentialsProvider.lookupCredentials(
            StandardUsernamePasswordCredentials.class, 
            Jenkins.instance
        );
        return creds.findResult { it.name == name ? [ it.username, it.password ] : null }
    }