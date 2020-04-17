import groovy.util.XmlParser
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class JobDslGenUtil {

    static void main(String[] args) {
        // println getJenkinsfileFromGitHub('https://raw.githubusercontent.com/sboardwell/job-dsl-generator/latest/examples/seed-project/Jenkinsfile', System.out, '')
    }

    /*
     * A little holder for the jenkinsfiles taken from github
     */
    private static gitHubJenkinsfiles = [:]

    /*
     * A little holder for the jenkinsfiles JSON object taken from github
     */
    private static jenkinsfilesJSONs = [:]

    private static printOutput(output, out) {
        output << '-'.multiply(90)
        output.each {
            out.println "$it"
        }
    }

    static def getUsernamePasswordOrSecretCredential(def credentialsId) {
        def creds = jenkins.model.Jenkins.instanceOrNull?.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')
        def ret = []
        creds.each { provider ->
            def cred = provider.credentials.findResult { it.id == credentialsId ? it : null }
            if (cred?.class.name == 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
                ret = [ cred.password, cred.username ]
            else if (cred?.class.name == 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl') {
                ret = [ cred.secret ]
            }
        }
        return ret
    }

    private static getResponse(url, body, authorization, postContentType) {
        def http = new URL(url).openConnection() as HttpURLConnection
        http.setRequestMethod("POST")
        http.setDoOutput(true)
        http.setRequestProperty("Authorization", authorization)
        http.setRequestProperty("Accept", "application/json")
        http.setRequestProperty("Cache-Control", "no-store")
        http.setRequestProperty("Content-Type", postContentType)
        http.outputStream.write(body.getBytes("UTF-8"))
        http.connect()
        if (http.responseCode == 200) {
            return http.inputStream.getText("UTF-8")
        } else {
            throw new Exception(http.errorStream.getText("UTF-8"))
        }
    }

    static String addParametersUsingDslJobDefinition(job, jenkinsUrl, jenkinsCreds, out, githubCreds = '') {
        String jenkinsfileStr = getJenkinsfileFromGitHubUsingDslJobDefinition(job, out, githubCreds)
        def pipelineJson = getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out)
        addParameters(job, pipelineJson, out)
    }

    static String addOptionsUsingDslJobDefinition(job, jenkinsUrl, jenkinsCreds, out, githubCreds = '') {
        String jenkinsfileStr = getJenkinsfileFromGitHubUsingDslJobDefinition(job, out, githubCreds)
        def pipelineJson = getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out)
        addOptions(job, pipelineJson, out)
    }

    static String addTriggersUsingDslJobDefinition(job, jenkinsUrl, jenkinsCreds, out, githubCreds = '') {
        String jenkinsfileStr = getJenkinsfileFromGitHubUsingDslJobDefinition(job, out, githubCreds)
        def pipelineJson = getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out)
        addTriggers(job, pipelineJson, out)
    }

    static String addExtrasUsingDslJobDefinition(job, jenkinsUrl, jenkinsCreds, out, githubCreds = '') {
        String jenkinsfileStr = getJenkinsfileFromGitHubUsingDslJobDefinition(job, out, githubCreds)
        def pipelineJson = getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out)
        addParameters(job, pipelineJson, out)
        addOptions(job, pipelineJson, out)
        addTriggers(job, pipelineJson, out)
    }

    static String getJenkinsfileFromGitHubUsingDslJobDefinition(job, out, githubCreds) {
        def rootNode = new XmlParser().parseText(job.xml)
        String scriptPathStr = rootNode.definition.scriptPath.text()
        String gitHubUrlStr = rootNode.definition.scm[0].userRemoteConfigs[0]."hudson.plugins.git.UserRemoteConfig".url.text()
        String credentialsId = rootNode.definition.scm[0].userRemoteConfigs[0]."hudson.plugins.git.UserRemoteConfig".credentialsId.text()
        String branchStr = rootNode.definition.scm[0].branches[0]."hudson.plugins.git.BranchSpec".name.text()
        String rawUrlStr = gitHubUrlStr.replace('github.com', 'raw.githubusercontent.com').replaceAll('\\.git$', '')
        String url = "${rawUrlStr}/${branchStr}/${scriptPathStr}"
        String token = githubCreds ?: credentialsId ? getUsernamePasswordOrSecretCredential(credentialsId)[0] : ''
        return getJenkinsfileFromGitHub(url, out, token)
    }

    static String getJenkinsfileFromGitHub(url, out, token = '') {
        out.println "Looking for url '${url}' with token '${token.take(5)}${token ? '...' : ''}'"
        def http = new URL(url).openConnection() as HttpURLConnection
        http.setRequestMethod("GET")
        http.setDoOutput(true)
        http.setRequestProperty("Cache-Control", "no-store")
        http.setRequestProperty("Content-Type", 'text/plain')
        if (token) {
            http.setRequestProperty("Authorization", "token ${token}")
        }
        http.connect()
        if (http.responseCode == 200) {
            return http.inputStream.getText("UTF-8")
        } else {
            throw new Exception(http.errorStream.getText("UTF-8"))
        }
    }

    static String getJenkinsfileFromGitHub(out, org, repo, branchStr, scriptPathStr, token = '') {
        def url = "https://raw.githubusercontent.com/${org}/${repo}/${branchStr}/${scriptPathStr}"
        return getJenkinsfileFromGitHub(url, out, token)
    }

    static def getJenkinsfileJsonObject(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out) {
        if (!jenkinsfilesJSONs.get(jenkinsfileStr)) {
            jenkinsfilesJSONs.put(jenkinsfileStr, getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out))
        }
        return jenkinsfilesJSONs.get(jenkinsfileStr)
    }

    static def getJenkinsfileJsonObjectFromJenkins(jenkinsUrl, jenkinsCreds, jenkinsfileStr, out) {
        if (!jenkinsfilesJSONs.get(jenkinsfileStr)) {
            // get the JSON
            String urlText = ''
            def jenkinsPipelineJson
            try {
                String basicAuth = "Basic " + new String(Base64.getEncoder().encode("${jenkinsCreds}".getBytes()));
                urlText = getResponse("${jenkinsUrl}/pipeline-model-converter/toJson", "jenkinsfile=${java.net.URLEncoder.encode(jenkinsfileStr, "UTF-8")}", "${basicAuth}", 'application/x-www-form-urlencoded')
            } catch (Exception e) {
                out.println "There was a problem fetching the json. ${e.message}"
                throw e
            }
            // validate JSON
            try {
                jenkinsPipelineJson = new JsonSlurper().parseText(urlText)
                // validate the response
                if (jenkinsPipelineJson.status != 'ok' || jenkinsPipelineJson.data.result != 'success') {
                    out.println "START\n${jenkinsPipelineJson.toString()}\nEND"
                    out.println JsonOutput.prettyPrint("${urlText}")
                    throw new Exception("Pipeline parsing error. See: the response above.")
                }
            } catch (Exception e) {
                out.println "There is a problem with the returned Jenkinsfile JSON object:"
                out.println "-".multiply(80)
                out.println "Jenkinsfile sent:"
                out.println "$jenkinsfileStr"
                out.println "-".multiply(80)
                out.println "Response from Jenkins:"
                out.println urlText
                throw e
            }
            jenkinsfilesJSONs.put(jenkinsfileStr, jenkinsPipelineJson.data.json)
        }
        return jenkinsfilesJSONs.get(jenkinsfileStr)
    }

    static def generateJobFromScratch(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, githubToken, jenkinsUrl, jenkinsCreds) {
        dslFactory.out.println "Generating pipeline job from scratch for '${jobName}'"
        String jenkinsfileStr = getJenkinsfileFromGitHub(dslFactory.out, org, repo, branchStr, scriptPathStr, githubToken)
        def pipelineJson = getJenkinsfileJsonObject(jenkinsUrl, jenkinsCreds, jenkinsfileStr, dslFactory.out)
        generateJobFull(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, pipelineJson, dslFactory.out)
    }

    static def generateJobFull(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description, pipelineJson, out) {
        def job = generateJobBase(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description)
        addParameters(job, pipelineJson, out)
        addOptions(job, pipelineJson, out)
        addTriggers(job, pipelineJson, out)
        return job
    }

    static def generateJobBase(dslFactory, jobName, org, repo, branchStr, credentialsId, scriptPathStr, displayName, description) {
        dslFactory.out.println "Generating base job for '${jobName}'"
        def job = dslFactory.pipelineJob("${jobName}") {
            if (displayName) {
                delegate.displayName displayName
            }
            if (description) {
                delegate.description description
            }
            definition {
                cpsScm {
                    scm {
                        git {
                            branch(branchStr)
                            remote {
                                github("${org}/${repo}", 'https')
                                if (credentialsId) {
                                    delegate.credentials(credentialsId)
                                }
                            }
                            extensions {
                                localBranch ''
                            }
                        }
                    }
                    lightweight()
                    scriptPath(scriptPathStr)
                }
            }
        }
        return job
    }

    static def addTriggers(job, pipelineJson, out) {
        def output = []
        try {
            // add triggers
            output << '-'.multiply(90)
            output << 'Calling addTriggers'
            def jobTriggers = pipelineJson.pipeline.triggers?.triggers
            if (jobTriggers) {
                job.with {
                    triggers {
                        def triggersDelegate = delegate
                        jobTriggers.each {
                            // currently only supporting literals
                            if (it.arguments[0].isLiteral) {
                                def triggerType = it.name
                                def valueToSet = it.arguments[0].value
                                output << "TRIGGER ----> ${triggerType} : ${valueToSet}"
                                switch (triggerType) {
                                    case ~/cron/:
                                        triggersDelegate.cron {
                                            spec(valueToSet)
                                        }
                                        break
                                    case ~/pollSCM/:
                                        triggersDelegate.pollSCM {
                                            scmpoll_spec(valueToSet)
                                        }
                                        break
                                    default:
                                        throw new Exception("Unhandled triggerType '${triggerType}'.")
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    static def addParameters(job, pipelineJson, out) {
        def output = []
        try {
            // add parameters
            output << '-'.multiply(90)
            output << 'Calling addParameters'
            def jobParameters = pipelineJson.pipeline.parameters?.parameters
            if (jobParameters) {
                job.with {
                    def jobDelegate = delegate
                    parameters {
                        def parametersDelegate = delegate
                        jobParameters.each { paramObj ->
                            def paramName = paramObj.arguments.find { a -> a.key == 'name' }.value.value
                            def paramType = paramObj.name
                            def argMap = [:]
                            output << "PARAMETER ----> $paramType :"
                            paramObj.arguments.each { arg ->
                                String argKey = arg.key
                                boolean isLiteral = arg.value.isLiteral as boolean
                                // currently only supporting literals
                                // if non-literal values found set to blank string to be corrected on first build run 
                                def valueToSet = ''
                                if (isLiteral)  {
                                    valueToSet = arg.value.value
                                }
                                output << "    Use literal: ${isLiteral} ---------> $argKey : ${valueToSet}"
                                argMap."$argKey" = valueToSet
                            }
                            switch (paramType) {
                                case ~/booleanParam/:
                                    addBooleanParam(parametersDelegate, argMap)
                                    break
                                case ~/string/:
                                    addStringParam(parametersDelegate, argMap)
                                    break
                                case ~/choice/:
                                    addChoiceParam(parametersDelegate, argMap)
                                    break
                                case ~/extendedChoice/:
                                    addExtendedChoiceParam(jobDelegate, argMap)
                                    break
                                case ~/text/:
                                    addTextParam(parametersDelegate, argMap)
                                    break
                                default:
                                    throw new Exception("Unhandled paramType '${paramType}'.")
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    static def addOptions(job, pipelineJson, out) {
        def output = []
        try {
            output << '-'.multiply(90)
            output << 'Calling addOptions'
            def jobOptions = pipelineJson.pipeline.options?.options
            if (jobOptions) {
                job.with {
                    //noinspection GroovyAssignabilityCheck
                    properties {
                        jobOptions.each { optionObj ->
                            def optionType = optionObj.name
                            output << "OPTION ----> $optionType"
                            switch (optionType) {
                                case ~/disableConcurrentBuilds/:
                                    delegate.disableConcurrentBuilds()
                                    break
                                case ~/skipDefaultCheckout/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/skipStagesAfterUnstable/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/timestamps/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/timeout/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/ansiColor/:
                                    output << "Ignoring pipeline only '${optionType}"
                                    break
                                case ~/buildDiscarder/:
                                    if (optionObj.arguments) {
                                        // currently only supporting the logRotator method
                                        def argMap = [:]
                                        def logRotatorArgs = optionObj.arguments.find { a -> a.name == 'logRotator' }?.arguments
                                        logRotatorArgs?.each { arg ->
                                            String argKey = arg.key
                                            boolean isLiteral = arg.value.isLiteral as boolean
                                            // currently only supporting literals
                                            // if non-literal values found set to blank string to be corrected on first build run 
                                            def valueToSet = ''
                                            if (isLiteral)  {
                                                valueToSet = arg.value.value
                                            }
                                            output << "    Use literal: ${isLiteral} ---------> $argKey : ${valueToSet}"
                                            argMap."${argKey}" = valueToSet
                                        }
                                        delegate.buildDiscarder {
                                            strategy {
                                                logRotator {
                                                    daysToKeepStr(argMap."daysToKeepStr" ?: '')
                                                    numToKeepStr(argMap."numToKeepStr" ?: '')
                                                    artifactDaysToKeepStr(argMap."artifactDaysToKeepStr" ?: '')
                                                    artifactNumToKeepStr(argMap."artifactNumToKeepStr" ?: '')
                                                }
                                            }
                                        }
                                    }
                                    break
                                default:
                                    throw new Exception("Unhandled optionType '${optionType}'.")
                            }
                        }
                    }
                }
            }
        } finally {
            printOutput(output, out)
        }
    }

    private static addStringParam(def delegate, def argMap) {
        delegate.stringParam(argMap.name, argMap.defaultValue, argMap.description)
    }

    private static addChoiceParam(def delegate, def argMap) {
        // special case for choices
        if (argMap.choices instanceof String) {
            argMap.choices = argMap.choices.split('\n') as List<String>
        }
        delegate.choiceParam(argMap.name, argMap.choices, argMap.description)
    }

    private static addExtendedChoiceParam(def jobDelegate, def argMap) {
        // special case for extended choice
        jobDelegate.configure { project->
            project / 'properties' / 'hudson.model.ParametersDefinitionProperty' / parameterDefinitions << 'com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition' {
                argMap.each { k, v ->
                    delegate.k(v)
                }
            }
        }
    }

    private static addTextParam(def delegate, def argMap) {
        delegate.textParam(argMap.name, argMap.defaultValue, argMap.description)
    }

    private static addBooleanParam(def delegate, def argMap) {
        delegate.booleanParam(argMap.name, argMap.defaultValue, argMap.description)
    }
}
