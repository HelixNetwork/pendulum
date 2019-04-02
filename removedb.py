import shutil
dbs = ['db', 'hxi', 'spent-addresses-log', 'spent-addresses-db', 'mainnet.log']
[shutil.rmtree(dir) for dir in dbs]
