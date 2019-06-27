
accept varInstance char prompt 'Instance                      ';
accept varPasswd   char prompt 'C$CSCHEM1 password           ' hide;
accept varUser     char prompt 'User name                     ';

connect C$CSCHEM1@&varInstance/&varPasswd;
@grants.sql;
quit;
