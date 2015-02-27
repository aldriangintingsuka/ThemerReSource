package com.gfamily.resource.themer;

import com.gfamily.resource.themer.Business.Managers.IIconPackManager;
import com.gfamily.resource.themer.Business.Managers.IconPackManager;
import com.google.gson.Gson;

import android.os.Bundle;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity
{

  @Override
  protected void onCreate( Bundle savedInstanceState )
  {
    super.onCreate( savedInstanceState );
    setContentView( R.layout.activity_main );

    final PackageManager pm = getPackageManager();
    final Gson jsonParser = new Gson();
    SharedPreferences preference = getSharedPreferences( "default", 0 );

    IIconPackManager iconPackManager = new IconPackManager( pm, jsonParser, preference, this );

    WebView webView = (WebView) findViewById( R.id.WebView );
    // Enable JavaScript
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled( true );
    settings.setAllowFileAccessFromFileURLs( true );
    settings.setCacheMode( WebSettings.LOAD_NO_CACHE );

    webView.addJavascriptInterface( iconPackManager, "iconPackManager" );

    // Load the entry point page into the webView.
    webView.loadUrl( "file:///android_asset/WebView/index.html" );
  }

  @Override
  public boolean onCreateOptionsMenu( Menu menu )
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate( R.menu.main, menu );
    return true;
  }
}
