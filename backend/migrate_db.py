import sqlite3

def migrate():
    conn = sqlite3.connect('envmatch.db')
    cursor = conn.cursor()
    
    print("Checking for missing columns...")
    
    # Tables and columns to add
    migrations = [
        ('tasks', 'input_tokens', 'FLOAT'),
        ('tasks', 'output_tokens', 'FLOAT'),
        ('task_results', 'input_tokens', 'FLOAT'),
        ('task_results', 'output_tokens', 'FLOAT'),
    ]
    
    for table, column, col_type in migrations:
        try:
            print(f"Adding column {column} to table {table}...")
            cursor.execute(f"ALTER TABLE {table} ADD COLUMN {column} {col_type}")
            print(f"Successfully added {column} to {table}")
        except sqlite3.OperationalError as e:
            if "duplicate column name" in str(e).lower():
                print(f"Column {column} already exists in {table}")
            else:
                print(f"Error adding {column} to {table}: {e}")
                
    conn.commit()
    conn.close()
    print("Migration finished.")

if __name__ == "__main__":
    migrate()
