package com.monke.monkeybook.presenter;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.provider.DocumentFile;
import android.widget.Toast;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;
import com.monke.basemvplib.BasePresenterImpl;
import com.monke.basemvplib.impl.IView;
import com.monke.monkeybook.R;
import com.monke.monkeybook.base.observer.SimpleObserver;
import com.monke.monkeybook.bean.BookSourceBean;
import com.monke.monkeybook.dao.DbHelper;
import com.monke.monkeybook.help.FileHelper;
import com.monke.monkeybook.help.RxBusTag;
import com.monke.monkeybook.model.BookSourceManage;
import com.monke.monkeybook.presenter.impl.IBookSourcePresenter;
import com.monke.monkeybook.service.CheckSourceService;
import com.monke.monkeybook.view.impl.IBookSourceView;

import java.io.File;
import java.net.URL;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static android.text.TextUtils.isEmpty;

/**
 * Created by GKF on 2017/12/18.
 * 书源管理
 */

public class BookSourcePresenterImpl extends BasePresenterImpl<IBookSourceView> implements IBookSourcePresenter {
    private BookSourceBean delBookSource;
    Snackbar snackbar;

    @Override
    public void saveData(BookSourceBean bookSourceBean) {
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            DbHelper.getInstance().getmDaoSession().getBookSourceBeanDao().insertOrReplace(bookSourceBean);
            BookSourceManage.refreshBookSource();
            e.onNext(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public void saveData(List<BookSourceBean> bookSourceBeans) {
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            for (int i = 1; i <= bookSourceBeans.size(); i++) {
                bookSourceBeans.get(i - 1).setSerialNumber(i);
            }
            DbHelper.getInstance().getmDaoSession().getBookSourceBeanDao().insertOrReplaceInTx(bookSourceBeans);
            BookSourceManage.refreshBookSource();
            e.onNext(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public void delData(BookSourceBean bookSourceBean) {
        this.delBookSource = bookSourceBean;
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            DbHelper.getInstance().getmDaoSession().getBookSourceBeanDao().delete(bookSourceBean);
            e.onNext(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Boolean>() {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        Snackbar.make(mView.getView(), delBookSource.getBookSourceName() + "已删除", Snackbar.LENGTH_LONG)
                                .setAction("恢复", view -> {
                                    restoreBookSource(delBookSource);
                                })
                                .show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(mView.getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                        mView.refreshBookSource();
                    }
                });
    }

    @Override
    public void delData(List<BookSourceBean> bookSourceBeans) {
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            for (BookSourceBean sourceBean : bookSourceBeans) {
                DbHelper.getInstance().getmDaoSession().getBookSourceBeanDao().delete(sourceBean);
            }
            BookSourceManage.refreshBookSource();
            e.onNext(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Boolean>() {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        Toast.makeText(mView.getContext(), "删除成功", Toast.LENGTH_SHORT).show();
                        mView.refreshBookSource();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(mView.getContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void restoreBookSource(BookSourceBean bookSourceBean) {
        Observable.create((ObservableOnSubscribe<Boolean>) e -> {
            BookSourceManage.addBookSource(bookSourceBean);
            BookSourceManage.refreshBookSource();
            e.onNext(true);
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Boolean>() {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        mView.refreshBookSource();
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }
                });
    }

    @Override
    public void importBookSource(Uri uri) {
        String json;
        if (uri.toString().startsWith("content://")) {
            json = FileHelper.readString(uri);
        } else {
            String path = uri.getPath();
            DocumentFile file = DocumentFile.fromFile(new File(path));
            json = FileHelper.readString(file);
        }
        if (!isEmpty(json)) {
            showSnackBar("正在导入书源", Snackbar.LENGTH_INDEFINITE);
            BookSourceManage.importBookSourceO(json)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getImportObserver());
        } else {
            Toast.makeText(mView.getContext(), "文件读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void importBookSource(String sourceUrl) {
        URL url;
        try {
            url = new URL(sourceUrl);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mView.getContext(), "URL格式不对", Toast.LENGTH_SHORT).show();
            return;
        }
        showSnackBar("正在导入书源", Snackbar.LENGTH_INDEFINITE);
        BookSourceManage.importSourceFromWww(url)
                .subscribe(getImportObserver());
    }

    private SimpleObserver<Boolean> getImportObserver() {
        return new SimpleObserver<Boolean>() {
            @Override
            public void onNext(Boolean aBoolean) {
                if (aBoolean) {
                    mView.refreshBookSource();
                    showSnackBar("导入成功", Snackbar.LENGTH_SHORT);
                } else {
                    showSnackBar("格式不对", Snackbar.LENGTH_SHORT);
                }
            }

            @Override
            public void onError(Throwable e) {
                showSnackBar(e.getMessage(), Snackbar.LENGTH_SHORT);
            }
        };
    }

    private void showSnackBar(String msg, int length) {
        Snackbar.make(mView.getView(), msg, length).show();
    }

    private String getProgressStr(int state) {
        return String.format(mView.getContext().getString(R.string.check_book_source) + mView.getContext().getString(R.string.progress_show),
                state, BookSourceManage.getAllBookSource().size());
    }

    @Override
    public void checkBookSource() {
        CheckSourceService.start(mView.getContext());
    }

    /////////////////////////////////////////////////

    @Override
    public void attachView(@NonNull IView iView) {
        super.attachView(iView);
        RxBus.get().register(this);
    }

    @Override
    public void detachView() {
        RxBus.get().unregister(this);
    }

    /////////////////////RxBus////////////////////////

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.CHECK_SOURCE_STATE)})
    public void upCheckSourceState(Integer state) {
        mView.refreshBookSource();

        if (state == -1) {
            showSnackBar("校验完成", Snackbar.LENGTH_SHORT);
        } else {
            if (snackbar == null) {
                snackbar = Snackbar.make(mView.getView(), getProgressStr(state), Snackbar.LENGTH_INDEFINITE);
            }
            snackbar.setText(getProgressStr(state));
            if (!snackbar.isShown()) {
                snackbar.show();
            }
        }
    }
}
