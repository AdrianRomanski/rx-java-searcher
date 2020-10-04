package pl.training.search;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import pl.training.search.github.GithubService;
import pl.training.search.wikipedia.Article;
import pl.training.search.wikipedia.WikipediaService;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.ArrayList;
import java.util.List;

public class Application {

    private final GithubService githubService = new GithubService(retrofitBuilder("https://api.github.com/"));
    private final WikipediaService wikipediaService = new WikipediaService(retrofitBuilder("https://en.wikipedia.org/w/"));
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private Retrofit retrofitBuilder(String url) {
        return new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(new OkHttpClient.Builder()
                        .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                        .build())
                .build();
    }

    private List<String> combine(List<String> result, String value) {
        List<String> newResult = new ArrayList<>(result);
        newResult.add(value);
        return newResult;
    }

    private Observable<List<String>> sendWikipediaQuery(String query) {
        return wikipediaService.getArticles(query)
                .flatMap(Observable::fromIterable)
                .map(Article::getTitle)
                .reduce(new ArrayList<>(), this::combine)
                .toObservable()
                .subscribeOn(Schedulers.io());
    }

    public static void main(String[] args) throws InterruptedException {
        new Application().start();
        Thread.sleep(10_000);
    }

    private void start() {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(compositeDisposable::dispose));
        compositeDisposable.add(
                ObservableReader.from(System.in)
                    .flatMap(this::sendWikipediaQuery)
                    .subscribe(System.out::println, System.out::println, () -> System.out.println("Completed"))
        );
    }
}
