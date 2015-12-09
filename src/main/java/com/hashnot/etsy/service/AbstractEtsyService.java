package com.hashnot.etsy.service;

import com.hashnot.async.Async;
import com.hashnot.etsy.dto.Response;
import rx.Observable;
import rx.Observer;
import rx.subjects.ReplaySubject;

import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Rafał Krupiński
 */
public class AbstractEtsyService extends Async {
    public static final int MAX_LIMIT = 100;
    protected final String apiKey;
    protected Executor executor;

    public AbstractEtsyService(String apiKey, Executor executor) {
        this.apiKey = apiKey;
        this.executor = executor;
    }

    static <O, I> O ns(I o, Function<I, O> conv) {
        return o == null ? null : conv.apply(o);
    }

    protected <T> Observable<Response<T>> call(ThrowingFunction<Integer, Response<T>> method) {
        ReplaySubject<Response<T>> result = ReplaySubject.create();
        Async.call(() -> method.apply(0), executor, new ResponseHandler<>(executor, method, result));
        return result;
    }

    private static class ResponseHandler<T> implements BiConsumer<Response<T>, Throwable> {
        private Executor executor;
        private ThrowingFunction<Integer, Response<T>> method;
        private Observer<Response<T>> observer;
        private int count = 0;

        private ResponseHandler(Executor executor, ThrowingFunction<Integer, Response<T>> method, Observer<Response<T>> observer) {
            this.executor = executor;
            this.method = method;
            this.observer = observer;
        }

        @Override
        public void accept(Response<T> result, Throwable throwable) {
            if (throwable != null)
                observer.onError(throwable);
            else {
                observer.onNext(result);
                count += result.getResults().size();

                if (result.getCount() > count)
                    Async.call(() -> method.apply(count), executor, this);
                else
                    observer.onCompleted();
            }
        }
    }

    protected interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
