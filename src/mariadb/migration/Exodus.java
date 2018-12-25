package mariadb.migration;

public class Exodus {
    public static void main(String[] args) {
        switch (args[0]) {
            case "MySQL":
                new mariadb.migration.mysql.MySQLMain();
                break;
            case "DB2":
            case "ORACLE":
                System.out.println("\n" + args[0] + " Code not ready yet!!!\n\n");
                break;
            default:
                System.out.println("\nUnknown Source Database!\nValid options: MySQL, DB2, ORACLE\n\n");
                break;
        }
        return;
    }
}
