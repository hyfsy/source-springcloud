
drop table if exists t_order;
create table t_order (
    id int primary key auto_increment,
    user_id int,
    store_id int,
    number int
);

drop table if exists t_money;
create table t_money (
    id int primary key auto_increment,
    user_id int,
    money decimal
);

drop table if exists t_store;
create table t_store (
    id int primary key auto_increment,
    number int
);

# insert into t_order values (1, 1, 1, 1);
insert into t_money values (1, 1, 1000);
insert into t_store values (1, 1000);
