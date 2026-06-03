package account;

import com.intuit.karate.junit5.Karate;

class AccountCrudTest {

    @Karate.Test
    Karate testAccountCrudLiberty() {
        return Karate.run("account-crud-liberty").relativeTo(getClass());
    }

    @Karate.Test
    Karate testAccountTransactionsLiberty() {
        return Karate.run("account-transactions-liberty").relativeTo(getClass());
    }
}
