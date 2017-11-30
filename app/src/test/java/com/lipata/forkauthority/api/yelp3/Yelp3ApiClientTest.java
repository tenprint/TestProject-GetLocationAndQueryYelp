package com.lipata.forkauthority.api.yelp3;

import android.annotation.SuppressLint;

import com.lipata.forkauthority.BuildConfig;
import com.lipata.forkauthority.api.yelp3.entities.SearchResponse;
import com.lipata.forkauthority.api.yelp3.entities.TokenResponse;
import com.lipata.forkauthority.data.AppSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import io.reactivex.observers.TestObserver;

/**
 * Created by jlipata on 6/4/17.
 */
public class Yelp3ApiClientTest {

    private final static String TEST_TOKEN_STRING =
            String.format(Yelp3Api.AUTH_FORMAT, BuildConfig.YELPFUSION_TEST_TOKEN);
    private static final String LATITUDE = "40.722091";
    private static final String LONGITUDE = "-73.843692";

    private Yelp3ApiClient api;
    private TokenManager tokenManager;

    @Before
    public void setUp() throws Exception {
        tokenManager = Mockito.mock(TokenManager.class);
        Mockito.when(tokenManager.getToken()).thenReturn(TEST_TOKEN_STRING);
        api = new Yelp3ApiClient(
                new Yelp3ApiAuthInterceptor(tokenManager),
                new Yelp3ApiAuthenticator(tokenManager));
    }

    @After
    public void tearDown() throws Exception {
    }

    @SuppressLint("DefaultLocale")
    @Test
    public void token() throws Exception {
        TestObserver<TokenResponse> testObserver = TestObserver.create();
        api
                .token(
                        Yelp3Api.GrantTypes.CLIENT_CREDENTIALS,
                        BuildConfig.YELPFUSION_CLIENT_ID,
                        BuildConfig.YELPFUSION_CLIENT_SECRET)
                .doOnSuccess(tokenResponse ->
                        System.out.println(String.format("Access token: %s | Expires in %d",
                                tokenResponse.getAccessToken(), tokenResponse.getExpiresIn())))
                .subscribe(testObserver);
        testObserver.assertNoErrors();
        testObserver.assertSubscribed();
    }



    @Test
    public void search() throws Exception {
        TestObserver<SearchResponse> testObserver = TestObserver.create();
        api
                .search(
                        "food",
                        LATITUDE,
                        LONGITUDE,
                        AppSettings.SEARCH_RADIUS,
                        Yelp3Api.SEARCH_LIMIT
                )
                .subscribe(testObserver);
        testObserver.assertNoErrors();
        testObserver.assertSubscribed();
    }

    @Test
    public void search1() throws Exception {
    }

}