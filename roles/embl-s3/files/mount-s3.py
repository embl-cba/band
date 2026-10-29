import tkinter as tk

import re
import subprocess
from tkinter import messagebox
from os.path import expanduser
import os

# S3 bucket names: 3-63 chars of lowercase letters, digits, dots and hyphens
BUCKET_RE = re.compile(r'^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$')


def is_mounted(path):
    # read the mount table instead of os.path.ismount(): stat() on a mount
    # whose bucket is unreachable blocks for as long as s3fs keeps retrying
    with open("/proc/self/mounts") as mounts:
        for line in mounts:
            target = line.split()[1].replace("\\040", " ")
            if target == path:
                return True
    return False


def bucket_answers(path):
    # s3fs mounts first and only then talks to S3, so try a listing, with a time limit
    try:
        return subprocess.run(["ls", "-1", path], stdout=subprocess.DEVNULL,
                              stderr=subprocess.DEVNULL, timeout=20).returncode == 0
    except subprocess.TimeoutExpired:
        return False


def stop_s3fs(path):
    # after a lazy unmount the daemon can keep retrying; stop this user's s3fs for path
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            if os.stat("/proc/" + pid).st_uid != os.getuid():
                continue
            with open("/proc/%s/cmdline" % pid, "rb") as f:
                argv = [a.decode("utf-8", "replace") for a in f.read().split(b"\0") if a]
        except OSError:
            continue
        if argv and os.path.basename(argv[0]) == "s3fs" and path in argv:
            try:
                os.kill(int(pid), 9)
            except OSError:
                pass


def mount():

    home = expanduser("~")
    bucket = bucket_entry.get().strip()

    # the bucket name becomes a directory in the home; don't let it go elsewhere
    if not BUCKET_RE.match(bucket) or '..' in bucket:
        messagebox.showerror("Error!!", "Not a valid bucket name: %r" % bucket)
        return

    mount_dir = home + "/" + bucket

    if access_entry.get() !="" and secret_entry.get() !="":
        public_flg = 0
    else:
        public_flg = 1

    # created 0600 from the start so the secret key is never readable by others;
    # a public bucket needs no keys, so leave any saved ones alone
    if public_flg == 0:
        pass_file = home + "/.passwd-s3fs"
        fd = os.open(pass_file, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w") as passwd_s3fs:
            passwd_s3fs.write(access_entry.get().strip() + ":" + secret_entry.get().strip())

    if is_mounted(mount_dir):
        messagebox.showerror(title="Error", message="Bucket is already mounted")
    else:
        os.makedirs(mount_dir, exist_ok=True)

        if public_flg ==0:
            if loc_var.get() =="EMBL":
                p1 = subprocess.Popen(["s3fs",bucket_entry.get(), mount_dir, "-o","url=https://s3.embl.de","-o","use_path_request_style"],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            elif loc_var.get()=="Others":
                p1 = subprocess.Popen(["s3fs", bucket_entry.get(), mount_dir, "-o", "url="+endpoint.get(), "-o", "use_path_request_style"],stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            else:
                p1 = subprocess.Popen(
                    ["s3fs", bucket_entry.get(), mount_dir],stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        else:
            if loc_var.get() =="EMBL":
                p1 = subprocess.Popen(["s3fs",bucket_entry.get(), mount_dir, "-o","url=https://s3.embl.de","-o","use_path_request_style","-o","public_bucket=1"],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            elif loc_var.get()=="Others":
                p1 = subprocess.Popen(["s3fs", bucket_entry.get(), mount_dir, "-o", "url="+endpoint.get(), "-o", "use_path_request_style","-o","public_bucket=1"],stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            else:
                p1 = subprocess.Popen(
                    ["s3fs", bucket_entry.get(), mount_dir,"-o","public_bucket=1"],stdout=subprocess.PIPE, stderr=subprocess.PIPE)


        try:
            output,err = p1.communicate(timeout=120)
        except subprocess.TimeoutExpired:
            p1.kill()
            output,err = p1.communicate()
        err = err.decode("utf-8", "replace").strip()

        # s3fs may print warnings and still mount, so judge by the mount itself
        if is_mounted(mount_dir) and bucket_answers(mount_dir):
            messagebox.showinfo("Success!!", "Your S3 bucket is mounted!")
            subprocess.Popen(['xdg-open', mount_dir], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            # wrong keys or no such bucket: s3fs is mounted but useless, take it down
            if is_mounted(mount_dir):
                subprocess.call(['fusermount', '-uz', mount_dir])
            stop_s3fs(mount_dir)
            try:
                os.rmdir(mount_dir)
            except OSError:
                pass
            messagebox.showerror("Error!!", (err + "\n\n" if err else "") + "Error mounting S3 bucket, please check your credentials and try again !")



def _get(*args):
    if loc_var.get()=="Others":
        endpoint.grid()
        typelbl.grid(row=1)
    else:
        endpoint.grid_remove()
        typelbl.grid_forget()


master = tk.Tk()
master.title("EMBL S3 client")

OptionList = [
    "EMBL",
    "AWS",
    "Others"
]

loc_var =tk.StringVar()
loc_var.set(OptionList[0])
#sv=OptionList[0]

location = tk.OptionMenu(master,loc_var,*OptionList, command=_get)
location.config(width=20)
location.grid(row=0,column=1,sticky="ew")
# location.pack(side="top")


tk.Label(master,text="S3 types").grid(row=0)
typelbl=tk.Label(master,text="Endpoint")
typelbl.grid(row=1)
tk.Label(master,text="Access Key").grid(row=2)
tk.Label(master,text="Secret Key").grid(row=3)
tk.Label(master,text="Bucket").grid(row=4)

endpoint = tk.Entry(master,width=30)
typelbl.grid_forget()
access_entry = tk.Entry(master,width=30)
secret_entry = tk.Entry(master,width=30)
bucket_entry = tk.Entry(master,width=30)

endpoint.grid(row=1,column = 1,padx=5,pady=5)
access_entry.grid(row=2,column = 1,padx=5,pady=5)
secret_entry.grid(row=3, column=1,padx=5,pady=5)
bucket_entry.grid(row=4,column=1,padx=5,pady=5)

endpoint.grid_remove()
tk.Button(master, text='Quit',command=master.quit).grid(row=5, column=0, sticky=tk.W, pady=4)
tk.Button(master, text='Mount', command=mount).grid(row=5, column=1, sticky=tk.W, pady=4)

master.mainloop()

