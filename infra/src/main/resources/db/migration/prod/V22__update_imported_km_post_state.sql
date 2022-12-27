update layout.km_post
set state = 'DELETED'
where state = 'NOT_IN_USE' and change_user = 'CSV_IMPORT'
