package com.gfamily.resource.themer.Business.Managers;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import com.google.gson.Gson;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IconPackManager implements IIconPackManager
{
  private PackageManager _pm;
  private Gson _jsonParser;
  private SharedPreferences _preference;
  private Activity _activity;
  private HashMap<String, HashMap<String, String>> _drawableMap;
  private String _iconMaskName;
  private String _packageName;

  public IconPackManager( PackageManager pm, Gson jsonParser, SharedPreferences preference, Activity activity )
  {
    _pm = pm;
    _jsonParser = jsonParser;
    _preference = preference;
    _activity = activity;
    _packageName = "com.gfamily.resource.themer";
  }

  @Override
  @JavascriptInterface
  public String GetIconPacks()
  {
    Log.d( _packageName, "get icon pack" );
    Intent mainIntent = new Intent( Intent.ACTION_MAIN, null );
    mainIntent.addCategory( "com.anddoes.launcher.THEME" );
    List<ResolveInfo> resolveInfos = _pm.queryIntentActivities( mainIntent, 0 );

    ArrayList<IconPack> iconPacks = new ArrayList<>();

    for( ResolveInfo resolveInfo : resolveInfos )
    {
      String packageName = resolveInfo.activityInfo.packageName;
      String packageLabel = resolveInfo.activityInfo.applicationInfo.loadLabel( _pm ).toString();
      Bitmap packageIcon = ( (BitmapDrawable) resolveInfo.activityInfo.applicationInfo.loadIcon( _pm ) ).getBitmap();
      packageIcon.setDensity( 480 );
      IconPack iconPack = new IconPack( packageName, packageLabel, packageIcon );
      iconPacks.add( iconPack );
    }

    String currentIconPack = _preference.getString( "currentIconPack", "" );

    HashMap<String, Object> resultMap = new HashMap<>();
    resultMap.put( "iconPacks", iconPacks );
    resultMap.put( "currentIconPack", currentIconPack );

    String result = _jsonParser.toJson( resultMap );
    Log.d( _packageName, "current icon pack " + currentIconPack );

    return result;
  }

  @Override
  @JavascriptInterface
  public String SetIconPack( String iconPackPackageName )
  {
    Log.d( _packageName, "set icon pack " + iconPackPackageName );
    _drawableMap = new HashMap<>();

    try
    {
      boolean isFromResources = GetDrawableMapFromResources( iconPackPackageName );

      if( !isFromResources )
        GetDrawableMapFromAssets( iconPackPackageName );

      SharedPreferences.Editor edit = _preference.edit();
      edit.putString( "currentIconPack", iconPackPackageName );
      edit.apply();

      WriteScriptFile( iconPackPackageName );

      Intent intent = new Intent( "com.gfamily.resource.UPDATE_SCRIPT" );
      intent.addCategory( Intent.CATEGORY_DEFAULT );
      intent.putExtra( "content", "icon theme" );
      _activity.startService( intent );
    }
    catch( Exception e )
    {
      Log.d( _packageName, e.getMessage() );
    }

    return null;
  }

  private boolean GetDrawableMapFromResources( String iconPackPackageName )
  {
    try
    {
      Resources themeResources = _pm.getResourcesForApplication( iconPackPackageName );
      int xmlID = themeResources.getIdentifier( "appfilter", "xml", iconPackPackageName );

      if( xmlID == 0 )
        return false;

      XmlResourceParser xmlParser = themeResources.getXml( xmlID );

      ParseXml( xmlParser );

      // indicate app done reading the resource.
      xmlParser.close();
    }
    catch( Exception e )
    {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  private void GetDrawableMapFromAssets( String iconPackPackageName )
  {
    try
    {
      Resources themeResources = _pm.getResourcesForApplication( iconPackPackageName );
      AssetManager assetManager = themeResources.getAssets();
      InputStream inputStream = assetManager.open( "appfilter.xml" );

      XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
      factory.setNamespaceAware( true );
      XmlPullParser xmlParser = factory.newPullParser();

      xmlParser.setInput( inputStream, null );
      ParseXml( xmlParser );
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
  }

  private void ParseXml( XmlPullParser xmlParser ) throws XmlPullParserException, IOException
  {
    int eventType = xmlParser.getEventType();

    while( eventType != XmlPullParser.END_DOCUMENT )
    {
      if( eventType == XmlPullParser.START_TAG && xmlParser.getName().equals( "item" ) )
      {
        String componentText = xmlParser.getAttributeValue( null, "component" );
        String drawableName = xmlParser.getAttributeValue( null, "drawable" );

        Pattern pattern = Pattern.compile( "ComponentInfo\\{(.+)/(.+)\\}" );
        Matcher matcher = pattern.matcher( componentText );

        String packageName = "";
        String activityName = "";

        if( matcher.find() )
        {
          packageName = matcher.group( 1 );
          activityName = matcher.group( 2 );
        }

        if( !_drawableMap.containsKey( packageName ) )
          _drawableMap.put( packageName, new HashMap<String, String>() );

        HashMap<String, String> activityMap = _drawableMap.get( packageName );
        activityMap.put( activityName, drawableName );
      }
      else if( eventType == XmlPullParser.START_TAG && xmlParser.getName().equals( "iconback" ) )
      {
        _iconMaskName = xmlParser.getAttributeValue( null, "img1" );
      }

      eventType = xmlParser.next();
    }
  }

  private void WriteScriptFile( String themePackageName )
  {
    BufferedWriter bw = null;
    File scriptFile = new File( Environment.getExternalStorageDirectory(), "com.gfamily.resource/Mods/IconThemer.txt" );

    try
    {
      bw = new BufferedWriter( new FileWriter( scriptFile ) );
      bw.write( "setBaseForwardPackage " + " " + themePackageName + "\n" );

      List<PackageInfo> packageInfos = _pm.getInstalledPackages( PackageManager.GET_ACTIVITIES );

      for( PackageInfo packageInfo : packageInfos )
      {
        String packageName = packageInfo.packageName;
        Log.d( _packageName, "Parsing app " + packageName );

        String appIconName = "";
        String appResourceType = "";
        String appResourcePackageName = "";
        String iconName;
        String resourceType;
        String resourcePackageName;

        try
        {
          Resources resourcesForApplication = _pm.getResourcesForApplication( packageName );
          Map<String, Boolean> activityIconNameMap = new HashMap<>();
          int appIconResource = packageInfo.applicationInfo.icon;

          if( appIconResource > 0 )
          {
            appIconName = resourcesForApplication.getResourceEntryName( appIconResource );
            appResourceType = resourcesForApplication.getResourceTypeName( appIconResource );
            appResourcePackageName = resourcesForApplication.getResourcePackageName( appIconResource );
            Log.d( _packageName, "Icon from app resource " + appIconName + " | " + appResourceType + " | " + appResourcePackageName );

            activityIconNameMap.put( appIconName, false );
            bw.write( "beginReplace " + packageName + " " + appResourceType + "\n" );
            bw.write( "withMaskResource " + appIconName + " " + _iconMaskName + ( appResourcePackageName.equals( packageName ) ? "" : " - " + appResourcePackageName ) + "\n" );
          }
          else
          {
            bw.write( "beginReplace " + packageName + " " + appResourceType + "\n" );
            bw.write( "withMaskResource " + appIconName + " " + _iconMaskName + ( appResourcePackageName.equals( packageName ) ? "" : " - " + appResourcePackageName ) + "\n" );
          }

          if( packageInfo.activities != null && packageInfo.activities.length != 0 )
          {
            for( ActivityInfo activityInfo : packageInfo.activities )
            {
              String activityName = activityInfo.name;

              try
              {
                int iconResource = activityInfo.getIconResource();

                if( iconResource > 0 )
                {
                  iconName = resourcesForApplication.getResourceEntryName( iconResource );
                  resourceType = resourcesForApplication.getResourceTypeName( iconResource );
                  resourcePackageName = resourcesForApplication.getResourcePackageName( iconResource );
                  Log.d( _packageName, "Icon from app activity " + iconName + " | " + resourceType + " | " + resourcePackageName );

                  String drawableName = _drawableMap.containsKey( packageName ) && _drawableMap.get( packageName ).containsKey( activityName ) ? _drawableMap.get( packageName ).get( activityName ) : null;

                  if( !activityIconNameMap.containsKey( iconName ) || !activityIconNameMap.get( iconName ) )
                  {
                    if( !activityIconNameMap.containsKey( iconName ) || !activityIconNameMap.get( iconName ) && drawableName != null )
                      bw.write( "beginReplace " + packageName + " " + resourceType + "\n" );

                    if( drawableName != null )
                    {
                      activityIconNameMap.put( iconName, true );
                      bw.write( "withResource " + iconName + " " + drawableName + ( resourcePackageName.equals( packageName ) ? "" : " - " + resourcePackageName ) + "\n" );
                    }
                    else if( !activityIconNameMap.containsKey( iconName ) )
                    {
                      activityIconNameMap.put( iconName, false );
                      bw.write( "withMaskResource " + iconName + " " + _iconMaskName + ( resourcePackageName.equals( packageName ) ? "" : " - " + resourcePackageName ) + "\n" );
                    }
                  }
                }
              }
              catch( Exception e )
              {
                e.printStackTrace();
              }
            }
          }
        }
        catch( Exception e )
        {
          e.printStackTrace();
        }
      }
    }
    catch( Exception e )
    {
      e.printStackTrace();
    }
    finally
    {
      if( bw != null )
        try
        {
          bw.close();
        }
        catch( IOException e )
        {
          e.printStackTrace();
        }
    }
  }

  private class IconPack
  {
    public String PackageName;
    public String PackageLabel;
    public String PackageIconString;

    public IconPack( String packageName, String packageLabel, Bitmap packageIcon )
    {
      PackageName = packageName;
      PackageLabel = packageLabel;

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      packageIcon.compress( Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream );
      byte[] byteArray = byteArrayOutputStream.toByteArray();
      String imageBase64 = Base64.encodeToString( byteArray, Base64.DEFAULT );
      PackageIconString = "data:image/png;base64," + imageBase64;
    }
  }
}
