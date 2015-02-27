function MainCtrl( $scope )
{
  $scope.IconPacks;
  $scope.GetApplications = GetApplications;
  $scope.SelectItem = SelectItem;
  $scope.CurrentIconPack;
  
  function GetApplications()
  {
    //var resolveInfos = [ { PackageName : "app 1", PackageLabel : "activity 1" }, { PackageName : "app 2", PackageLabel : "activity 2" } ];
    var resolveInfos = JSON.parse( iconPackManager.GetIconPacks() );
    $scope.IconPacks = resolveInfos.iconPacks;
    $scope.CurrentIconPack = resolveInfos.currentIconPack;
  }

  function SelectItem( iconPack )
  {
    // alert('item selected = ' + iconPack );
    iconPackManager.SetIconPack( iconPack );
    $scope.CurrentIconPack = iconPack;
  }
}
