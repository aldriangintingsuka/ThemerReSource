function TitleCtrl( $scope )
{
  var _titlePrefix = 'Icon Theme Generator for Re Source';

  $scope.$on( 'TitleChanged', HandleTitleChanged );

  $scope.Title = _titlePrefix;

  function HandleTitleChanged( event, title )
  {
    if( title )
      $scope.Title = _titlePrefix + ' - ' + title;
  }
}
