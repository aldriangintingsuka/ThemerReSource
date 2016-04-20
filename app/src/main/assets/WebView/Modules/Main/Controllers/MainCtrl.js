function MainCtrl( $scope, $timeout )
{
  $scope.IconPacks;
  $scope.GetApplications = GetApplications;
  $scope.SelectItem = SelectItem;
  $scope.CurrentIconPack;
  $scope.Status = 0;
  
  function GetApplications()
  {
    //var resolveInfos = [ { PackageName : "app 1", PackageLabel : "activity 1" }, { PackageName : "app 2", PackageLabel : "activity 2" } ];
    var resolveInfos = JSON.parse( iconPackManager.GetIconPacks() );
    $scope.IconPacks = resolveInfos.iconPacks;
    $scope.CurrentIconPack = resolveInfos.currentIconPack;
    $scope.Status = 0;
  }

  function SelectItem( iconPack )
  {
    // alert('item selected = ' + iconPack );
    $scope.CurrentIconPack = iconPack;
    $scope.Status = 1;
    $timeout( SetIconPack , 100 ).then( UpdateStatus );
  }

  function SetIconPack()
  {
    iconPackManager.SetIconPack(  $scope.CurrentIconPack );
  }

  function UpdateStatus()
  {
    $scope.Status = 2;
  }
}
