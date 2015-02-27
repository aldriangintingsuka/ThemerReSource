var mainModule = angular.module( "Gylee", ['ui.bootstrap'] );

mainModule.config( function( $sceDelegateProvider )
  {
    $sceDelegateProvider.resourceUrlWhitelist( ['self'] );
  } );

