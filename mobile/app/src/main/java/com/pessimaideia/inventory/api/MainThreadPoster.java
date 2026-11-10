package com.pessimaideia.inventory.api;

public interface MainThreadPoster {

    void post(Runnable runnable);

    /** For tests: runs immediately on the calling thread. */
    MainThreadPoster DIRECT = new MainThreadPoster() {
        @Override
        public void post(Runnable runnable) {
            runnable.run();
        }
    };
}