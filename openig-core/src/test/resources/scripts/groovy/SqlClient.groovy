import groovy.sql.Sql

/**
 * Access a database with a well-known structure,
 * in particular to get credentials given an email address.
 */
class SqlClient {

    // By default use an in-memory H2 test database.

    String url = "jdbc:h2:mem:test"
    String username = "sa"
    String password = ""
    String driver = "org.h2.Driver"
    def sql = Sql.newInstance(url, username, password, driver)

    // The expected table is laid out like the following.

    // Table CREDENTIALS
    // ------------------------------------
    // |    UID    | PASSWORD |    MAIL   |
    // ------------------------------------
    // | <user ID> | <passwd> | <mail@...>|
    // ------------------------------------

    String usernameColumn = "UID"
    String passwordColumn = "PASSWORD"
    String tableName = "CREDENTIALS"
    String mailColumn = "MAIL"

    /**
     * Get the Username and Password given an email address.
     *
     * @param mail Email address used to look up the credentials
     * @return Username and Password from the database
     */
    def getCredentials(mail) {
        def credentials = [:]
        def query = "SELECT " + usernameColumn + ", " + passwordColumn +
                " FROM " + tableName + " WHERE " + mailColumn + "='$mail';"

        sql.eachRow(query) {
            credentials.put("Username", it."$usernameColumn")
            credentials.put("Password", it."$passwordColumn")
        }
        return credentials
    }
}