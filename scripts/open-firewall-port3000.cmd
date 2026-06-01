@echo off
:: Запустите от имени администратора (ПКМ → Запуск от имени администратора)
netsh advfirewall firewall add rule name="LibBookServer 3000" dir=in action=allow protocol=TCP localport=3000
echo Rule added. Phone on Wi-Fi can use http://192.168.3.167:3000
pause
